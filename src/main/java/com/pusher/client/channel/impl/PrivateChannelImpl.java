package com.pusher.client.channel.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.Authorizer;
import com.pusher.client.channel.ChannelState;
import com.pusher.client.channel.PrivateChannel;
import com.pusher.client.channel.PrivateChannelEventListener;
import com.pusher.client.channel.SubscriptionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.impl.InternalConnection;
import com.pusher.client.util.Factory;

public class PrivateChannelImpl extends ChannelImpl implements PrivateChannel {

    private static final String CLIENT_EVENT_PREFIX = "client-";
    private final InternalConnection connection;
    private final Authorizer authorizer;

    public PrivateChannelImpl(final InternalConnection connection, final String channelName,
            final Authorizer authorizer, final Factory factory) {
        super(channelName, factory);
        this.connection = connection;
        this.authorizer = authorizer;
    }

    /* PrivateChannel implementation */

    @Override
    @SuppressWarnings("rawtypes")
    public void trigger(final String eventName, final String data) {

        if (eventName == null || !eventName.startsWith(CLIENT_EVENT_PREFIX)) {
            throw new IllegalArgumentException("Cannot trigger event " + eventName
                    + ": client events must start with \"client-\"");
        }

        if (state != ChannelState.SUBSCRIBED) {
            throw new IllegalStateException("Cannot trigger event " + eventName + " because channel " + name
                    + " is in " + state.toString() + " state");
        }

        if (connection.getState() != ConnectionState.CONNECTED) {
            throw new IllegalStateException("Cannot trigger event " + eventName + " because connection is in "
                    + connection.getState().toString() + " state");
        }

        try {
            final Map userData = new Gson().fromJson(data, Map.class);

            final Map<Object, Object> jsonPayload = new LinkedHashMap<Object, Object>();
            jsonPayload.put("event", eventName);
            jsonPayload.put("channel", name);
            jsonPayload.put("data", userData);

            final String jsonMessage = new Gson().toJson(jsonPayload);
            connection.sendMessage(jsonMessage);

        }
        catch (final JsonSyntaxException e) {
            throw new IllegalArgumentException("Cannot trigger event " + eventName + " because \"" + data
                    + "\" could not be parsed as valid JSON");
        }
    }

    /* Base class overrides */

    @Override
    public void bind(final String eventName, final SubscriptionEventListener listener) {

        if (listener instanceof PrivateChannelEventListener == false) {
            throw new IllegalArgumentException(
                    "Only instances of PrivateChannelEventListener can be bound to a private channel");
        }

        super.bind(eventName, listener);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public String toSubscribeMessage() {

        final String authResponse = getAuthResponse();

        try {
            final Map authResponseMap = new Gson().fromJson(authResponse, Map.class);
            final String authKey = (String)authResponseMap.get("auth");

            final Map<Object, Object> jsonObject = new LinkedHashMap<Object, Object>();
            jsonObject.put("event", "pusher:subscribe");

            final Map<Object, Object> dataMap = new LinkedHashMap<Object, Object>();
            dataMap.put("channel", name);
            dataMap.put("auth", authKey);

            jsonObject.put("data", dataMap);

            final String json = new Gson().toJson(jsonObject);
            return json;
        }
        catch (final Exception e) {
            throw new AuthorizationFailureException("Unable to parse response from Authorizer: " + authResponse, e);
        }
    }

    @Override
    protected String[] getDisallowedNameExpressions() {
        return new String[] { "^(?!private-).*" };
    }

    /**
     * Protected access because this is also used by PresenceChannelImpl.
     */
    protected String getAuthResponse() {
        final String socketId = connection.getSocketId();
        return authorizer.authorize(getName(), socketId);
    }

    @Override
    public String toString() {
        return String.format("[Private Channel: name=%s]", name);
    }
}
