package org.xersys.clients.pojo;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Objects;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import org.json.simple.JSONObject;
import org.xersys.commander.contants.RecordStatus;
import org.xersys.commander.iface.XEntity;

@Entity
@Table(name="Client_Address")

public class Client_Address implements Serializable, XEntity {
    @Id
    @Basic(optional = false)
    @Column(name = "sClientID")
    private String sClientID;
        
    @Id
    @Basic(optional = false)
    @Column(name = "nEntryNox")
    private int nEntryNox;
    
    @Column(name = "sHouseNox")
    private String sHouseNox;
    
    @Column(name = "sAddressx")
    private String sAddressx;
    
    @Column(name = "sBrgyIDxx")
    private String sBrgyIDxx;
    
    @Column(name = "sTownIDxx")
    private String sTownIDxx;
    
    @Basic(optional = false)
    @Column(name = "nPriority")
    private int nPriority;
    
    @Column(name = "nLatitude")
    private Number nLatitude;
    
    @Column(name = "nLongitud")
    private Number nLongitud;
    
    @Column(name = "cPrimaryx")
    private String cPrimaryx;
    
    @Column(name = "cRecdStat")
    private String cRecdStat;
    
    LinkedList laColumns = null;
    
    public Client_Address(){
        laColumns = new LinkedList();
        laColumns.add("sClientID");
        laColumns.add("nEntryNox");	
        laColumns.add("sHouseNox");
        laColumns.add("sAddressx");
        laColumns.add("sBrgyIDxx");
        laColumns.add("sTownIDxx");
        laColumns.add("nPriority");
        laColumns.add("nLatitude");
        laColumns.add("nLongitud");
        laColumns.add("cPrimaryx");
        laColumns.add("cRecdStat");
        
        sClientID = "";
        nEntryNox = -1;	
        sHouseNox = "";
        sAddressx = "";
        sBrgyIDxx = "";
        sTownIDxx = "";
        nPriority = -1;
        nLatitude = 0.00;
        nLongitud = 0.00;
        cPrimaryx = "";
        cRecdStat = "";
    }
    
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Client_Address)) return false;
        
        Client_Address other = (Client_Address) object;
        
        return !((sClientID == null && other.sClientID != null) || 
                (sClientID != null && !sClientID.equals(other.sClientID))) &&
                nEntryNox != other.nEntryNox;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(sClientID);
        hash = 47 * hash + nEntryNox;
        return hash;
    }
    
    @Override
    public String toString() {
        return this.getClass().getName() + "[sClientID=" + sClientID + ", nEntryNox=" + nEntryNox + "]";
    }
    
    @Override
    public Object getValue(int fnColumn) {
        switch(fnColumn){
            case 1: return sClientID;
            case 2: return nEntryNox;	
            case 3: return sHouseNox;
            case 4: return sAddressx;
            case 5: return sBrgyIDxx;
            case 6: return sTownIDxx;
            case 7: return nPriority;
            case 8: return nLatitude;
            case 9: return nLongitud;
            case 10: return cPrimaryx;
            case 11: return cRecdStat;
            default: return null;
        }
    }

    @Override
    public Object getValue(String fsColumn) {
        int lnCol = getColumn(fsColumn);
        
        if (lnCol > 0){
            return getValue(lnCol);
        } else
            return null;
    }

    @Override
    public String getColumn(int fnCol) {
        if (laColumns.size() < fnCol){
            return "";
        } else 
            return (String) laColumns.get(fnCol - 1);
    }

    @Override
    public int getColumn(String fsCol) {
        return laColumns.indexOf(fsCol) + 1;
    }

    @Override
    public void setValue(int fnColumn, Object foValue) {
        switch(fnColumn){
            case 1: sClientID = (String) foValue; break;
            case 2: nEntryNox = Integer.parseInt(String.valueOf(foValue)); break;
            case 3: sHouseNox = (String) foValue; break;
            case 4: sAddressx = (String) foValue; break;
            case 5: sBrgyIDxx = (String) foValue; break;
            case 6: sTownIDxx = (String) foValue; break;
            case 7: nPriority = Integer.parseInt(String.valueOf(foValue)); break;
            case 8: nLatitude = (Number) foValue; break;
            case 9: nLongitud = (Number) foValue; break;
            case 10: cPrimaryx = (String) foValue; break;
        }     
    }

    @Override
    public void setValue(String fsColumn, Object foValue) {
        int lnCol = getColumn(fsColumn);
        if (lnCol > 0){
            setValue(lnCol, foValue);
        }
    }

    @Override
    public int getColumnCount() {
        return laColumns.size();
    }

    @Override
    public String getTable() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toJSONString() {
        JSONObject loJSON = new JSONObject();
        
        for(int i = 0; i < laColumns.size(); i++){
            loJSON.put(laColumns.get(i), getValue(getColumn(i + 1)));
        }
        
        return loJSON.toJSONString();
    }

    @Override
    public void list() {
        for(int i = 0; i < laColumns.size(); i++){
            System.out.println(laColumns.get(i));
        }
    }
}
