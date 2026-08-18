/*
 * Copyright (c) 2026 FrancoGiudans
 *
 * This file is part of Capsulyric.
 *
 * Capsulyric is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Capsulyric is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 * AOSP hidden API stub (android.net.IConnectivityManager), derived from
 * InstallerX-Revived (https://github.com/wxxsfxyzm/InstallerX-Revived, GPL-3.0),
 * which itself mirrors the AOSP interface. Only firewall-related methods are
 * kept and original comments were removed. See THIRD_PARTY_NOTICES.md §5.
 */
package android.net;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IConnectivityManager extends IInterface {
    void setFirewallChainEnabled(int chain, boolean enable) throws RemoteException;

    void setUidFirewallRule(int chain, int uid, int rule) throws RemoteException;

    int getUidFirewallRule(int chain, int uid) throws RemoteException;

    abstract class Stub extends Binder implements IConnectivityManager {
        public static IConnectivityManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
