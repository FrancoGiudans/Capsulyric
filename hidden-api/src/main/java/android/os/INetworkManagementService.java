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
 * AOSP hidden API stub (android.os.INetworkManagementService), written for
 * Capsulyric to expose firewall-related methods via reflection. It mirrors
 * the AOSP interface signatures; no AOSP source code is copied. See
 * THIRD_PARTY_NOTICES.md §5.
 */
package android.os;

public interface INetworkManagementService extends IInterface {
    void setFirewallChainEnabled(int chain, boolean enable) throws RemoteException;

    void setUidFirewallRule(int chain, int uid, int rule) throws RemoteException;

    void setFirewallUidRule(int chain, int uid, int rule) throws RemoteException;

    abstract class Stub extends Binder implements INetworkManagementService {
        public static INetworkManagementService asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
