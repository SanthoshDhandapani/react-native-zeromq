package org.zeromq.rnzeromq;

import android.util.Log;

import java.util.UUID;
import java.util.HashMap;
import java.lang.String;
import java.lang.Boolean;
import java.util.Map;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import org.zeromq.ZMQ;


class ReactNativeZeroMQ extends ReactContextBaseJavaModule {

    final String TAG = "ReactNativeZeroMQ";

    private Map<String, Object> objectStorage;
    private final ZMQ.Context   zmqContext;

    ReactNativeZeroMQ(final ReactApplicationContext reactContext) {
        super(reactContext);
        zmqContext    = ZMQ.context(1);
        objectStorage = new HashMap<>();
    }

    @Override
    public String getName() {
        return "ReactNativeZeroMQAndroid";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();

        constants.put("ZMQ_REP",  ZMQ.REP);
        constants.put("ZMQ_REQ",  ZMQ.REQ);
        constants.put("ZMQ_XREP", ZMQ.XREP);
        constants.put("ZMQ_XREQ", ZMQ.XREQ);

        constants.put("ZMQ_PUB",  ZMQ.PUB);
        constants.put("ZMQ_SUB",  ZMQ.SUB);
        constants.put("ZMQ_XPUB", ZMQ.XPUB);
        constants.put("ZMQ_XSUB", ZMQ.XSUB);

        constants.put("ZMQ_DONTWAIT", ZMQ.DONTWAIT);
        constants.put("ZMQ_SNDMORE",  ZMQ.SNDMORE);

        constants.put("ZMQ_DEALER", ZMQ.DEALER);
        constants.put("ZMQ_ROUTER", ZMQ.ROUTER);

        // @TODO: add socket options constants

        return constants;
    }

    private String _newObject(Object obj) {
        UUID uuid = UUID.randomUUID();
        objectStorage.put(uuid.toString(), obj);
        return uuid.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> T _getObject(final String uuid) throws Exception {
        if (!objectStorage.containsKey(uuid)) {
            throw new ReactException("ENULLPTR", "No such object with key \"" + uuid + "\"");
        }
        return (T) objectStorage.get(uuid);
    }

    private Boolean _delObject(final String uuid) {
        if (objectStorage.containsKey(uuid)) {
            objectStorage.remove(uuid);
            return true;
        }
        return false;
    }

    private ZMQ.Socket _socket(final Integer socType) {
        return zmqContext.socket(socType);
    }

    private String _getDeviceIdentifier() {
        String devFriendlyName = ReactNativeUtils.getDeviceName();
        devFriendlyName = devFriendlyName.replaceAll("\\s", "_");
        return ("android.os.Build." + devFriendlyName + " " + ReactNativeUtils.getIPAddress(true));
    }

    @ReactMethod
    @SuppressWarnings("unused")
    public void socketCreate(final Integer socType, final Callback callback) {
        (new ReactTask(callback) {
            @Override
            Object run() throws Exception {
                ZMQ.Socket socket = ReactNativeZeroMQ.this._socket(socType);

                if (socket == null) {
                    return "";
                }

                return ReactNativeZeroMQ.this._newObject(socket);
            }
        }).start();
    }

    @ReactMethod
    @SuppressWarnings("unused")
    public void socketBind(final String uuid, final String addr, final Callback callback) {
        (new ReactTask(callback) {
            @Override
            Object run() throws Exception {
                ZMQ.Socket socket = ReactNativeZeroMQ.this._getObject(uuid);
                socket.bind(addr);
                return this._successResult(true);
            }
        }).start();
    }

    @ReactMethod
    @SuppressWarnings("unused")
    public void socketConnect(final String uuid, final String addr, final Callback callback) {
        (new ReactTask(callback) {
            @Override
            Object run() throws Exception {
                ZMQ.Socket socket = ReactNativeZeroMQ.this._getObject(uuid);
                socket.connect(addr);

                return this._successResult(true);
            }
        }).start();
    }

    @ReactMethod
    @SuppressWarnings("unused")
    public void socketClose(final String uuid, final Callback callback) {
        (new ReactTask(callback) {
            @Override
            Object run() throws Exception {
                ZMQ.Socket socket = ReactNativeZeroMQ.this._getObject(uuid);
                socket.close();

                ReactNativeZeroMQ.this._delObject(uuid);
                return this._successResult(true);
            }
        }).start();
    }

    @ReactMethod
    @SuppressWarnings("unused")
    public void destroy(final String uuid, final Callback callback) {
        (new ReactTask(callback) {
            @Override
            Object run() throws Exception {
                ZMQ.Socket socket = ReactNativeZeroMQ.this._getObject(uuid);
                socket.close();

                ReactNativeZeroMQ.this._delObject(uuid);
                return this._successResult(true);
            }
        }).start();
    }

    @ReactMethod
    @SuppressWarnings("unused")
    public void setSocketIdentity(final String uuid, final String id, final Callback callback) {
        (new ReactTask(callback) {
            @Override
            Object run() throws Exception {
                ZMQ.Socket socket = ReactNativeZeroMQ.this._getObject(uuid);
                socket.setIdentity(id.getBytes(ZMQ.CHARSET));

                return this._successResult(true);
            }
        }).start();
    }

    @ReactMethod
    @SuppressWarnings("unused")
    public void socketSend(final String uuid, final String body, final Integer flag, final Callback callback) {
        (new ReactTask(callback) {
            @Override
            Object run() throws Exception {
                ZMQ.Socket socket = ReactNativeZeroMQ.this._getObject(uuid);
                return socket.send(body, flag);
            }
        }).startAsync();
    }

    @ReactMethod
    @SuppressWarnings("unused")
    public void socketRecv(final String uuid, final Integer flag, final Integer poolInterval, final Callback callback) {
        (new ReactTask(callback) {
            @Override
            Object run() throws Exception {
                ZMQ.Socket socket = ReactNativeZeroMQ.this._getObject(uuid);

                if (poolInterval > (-1)) {
                    return ReactNativeZeroMQ.this._poolSocketPooling(socket, flag, poolInterval);
                }

                return socket.recvStr(flag);
            }
        }).startAsync();
    }

    private String _poolSocketPooling(ZMQ.Socket socket, final Integer flag, final Integer poolInterval) {
        StringBuilder strBuffer = new StringBuilder();

        ZMQ.PollItem  items []  = { new ZMQ.PollItem(socket, ZMQ.Poller.POLLIN) };
        int rc = ZMQ.poll(items, poolInterval);

        if (rc != -1) {
            Log.d(TAG, "Pooller init ok");
            if (items[0].isReadable()) {
                Log.d(TAG, "Has data...");

                do {
                    String recv = socket.recvStr(flag);
                    if (recv != null) {
                        Log.d(TAG, "Recv data: " + recv);
                        strBuffer.append(recv);
                        strBuffer.append("\n");
                    }
                } while (socket.hasReceiveMore());
            }
        } else {
            Log.d(TAG, "Pooller failed");
        }

        return strBuffer.toString();
    }

    @ReactMethod
    @SuppressWarnings("unused")
    public void socketHasMore(final String uuid, final Callback callback) {
        (new ReactTask(callback) {
            @Override
            Object run() throws Exception {
                ZMQ.Socket socket = ReactNativeZeroMQ.this._getObject(uuid);
                return socket.hasReceiveMore();
            }
        }).start();
    }

    @ReactMethod
    @SuppressWarnings("unused")
    public void getDeviceIdentifier(final Callback callback) {
        (new ReactTask(callback) {
            @Override
            Object run() throws Exception {
                return ReactNativeZeroMQ.this._getDeviceIdentifier();
            }
        }).start();
    }
}
