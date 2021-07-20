package org.xersys.clients.base;

public interface LClients {
    void MasterRetreive(String fsFieldNm, Object foValue);
    void MobileRetreive(int fnRow, String fsFieldNm, Object foValue);
    void AddressRetreive(int fnRow, String fsFieldNm, Object foValue);
    void EMailRetreive(int fnRow, String fsFieldNm, Object foValue);
}
