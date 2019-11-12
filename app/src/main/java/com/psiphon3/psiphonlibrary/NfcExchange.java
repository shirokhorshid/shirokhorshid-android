package com.psiphon3.psiphonlibrary;

import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;

@AutoValue
abstract class NfcExchange {
    public enum Type {
        EXPORT,
        IMPORT,
    }

    public abstract Type type();

    @Nullable
    public abstract String payload();

    public abstract Boolean success();

    public static NfcExchange exported(@Nullable String payload) {
        return new AutoValue_NfcExchange(Type.EXPORT, payload, true);
    }

    public static NfcExchange imported(Boolean success) {
        return new AutoValue_NfcExchange(Type.IMPORT, null, success);
    }
}
