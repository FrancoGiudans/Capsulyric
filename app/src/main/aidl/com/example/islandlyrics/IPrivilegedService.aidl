package com.example.islandlyrics;

import com.example.islandlyrics.IPrivilegedLogCallback;

interface IPrivilegedService {
    void setLogCallback(IPrivilegedLogCallback callback);
    boolean setPackageNetworkingEnabled(int uid, boolean enabled);
}
