package org.xersys.clients.pojo;

import java.io.Serializable;
import java.util.Date;
import java.util.LinkedList;
import java.util.Objects;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import org.json.simple.JSONObject;
import org.xersys.commander.contants.RecordStatus;
import org.xersys.commander.iface.XEntity;
import org.xersys.commander.util.SQLUtil;

@Entity
@Table(name="Client_Master")

public class Client_Master implements Serializable, XEntity {
    @Id
    @Basic(optional = false)
    @Column(name = "sClientID")
    private String sClientID;
    
    @Column(name = "cClientTp")
    private String cClientTp;
    
    @Column(name = "sLastName")
    private String sLastName;
    
    @Column(name = "sFrstName")
    private String sFrstName;
    
    @Column(name = "sMiddName")
    private String sMiddName;
    
    @Column(name = "sSuffixNm")
    private String sSuffixNm;
    
    @Column(name = "sClientNm")
    private String sClientNm;
    
    @Column(name = "cGenderCd")
    private String cGenderCd;
    
    @Column(name = "cCvilStat")
    private String cCvilStat;
    
    @Column(name = "sCitizenx")
    private String sCitizenx;
    
    @Basic(optional = false)
    @Temporal(TemporalType.DATE)
    @Column(name = "dBirthDte")
    private Date dBirthDte;
     
    @Column(name = "sBirthPlc")
    private String sBirthPlc;
    
    @Column(name = "sAddlInfo")
    private String sAddlInfo;
    
    @Column(name = "sSpouseID")
    private String sSpouseID;
    
    @Column(name = "cLRClient")
    private String cLRClient;
    
    @Column(name = "cMCClient")
    private String cMCClient;
    
    @Column(name = "cSCClient")
    private String cSCClient;
    
    @Column(name = "cSPClient")
    private String cSPClient;
    
    @Column(name = "cCPClient")
    private String cCPClient;
    
    @Column(name = "cRecdStat")
    private String cRecdStat;   
    
    @Basic(optional = false)
    @Column(name = "dModified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dModified;
    
    @Column(name = "cSupplier")
    private String cSupplier;
    
    LinkedList laColumns = null;
    
    public Client_Master(){
        laColumns = new LinkedList();
        laColumns.add("sClientID");
        laColumns.add("cClientTp");
        laColumns.add("sLastName");
        laColumns.add("sFrstName");
        laColumns.add("sMiddName");
        laColumns.add("sSuffixNm");
        laColumns.add("sClientNm");
        laColumns.add("cGenderCd");
        laColumns.add("cCvilStat");
        laColumns.add("sCitizenx");
        laColumns.add("dBirthDte");
        laColumns.add("sBirthPlc");
        laColumns.add("sAddlInfo");
        laColumns.add("sSpouseID");
        laColumns.add("cLRClient");
        laColumns.add("cMCClient");
        laColumns.add("cSCClient");
        laColumns.add("cSPClient");
        laColumns.add("cCPClient");
        laColumns.add("cRecdStat");
        laColumns.add("dModified");
        laColumns.add("cSupplier");
        
        sClientID = "";
        cClientTp = "";
        sLastName = "";
        sFrstName = "";
        sMiddName = "";
        sSuffixNm = "";
        sClientNm = "";
        cGenderCd = "";
        cCvilStat = "";
        sCitizenx = "";
        sBirthPlc = "";
        sAddlInfo = "";
        sSpouseID = "";
        cLRClient = "0";
        cMCClient = "0";
        cSCClient = "0";
        cSPClient = "0";
        cCPClient = "0";
        cRecdStat = RecordStatus.ACTIVE;
        cSupplier = "0";
    }
    
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Client_Master)) return false;
        
        Client_Master other = (Client_Master) object;
        
        return !((this.sClientID == null && other.sClientID != null) || 
                (this.sClientID != null && !this.sClientID.equals(other.sClientID)));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.sClientID);
        return hash;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[sClientID=" + sClientID + "]";
    }
    
    @Override
    public Object getValue(int fnColumn) {
        switch(fnColumn){
            case 1: return sClientID;
            case 2: return cClientTp;
            case 3: return sLastName;
            case 4: return sFrstName;
            case 5: return sMiddName;
            case 6: return sSuffixNm;
            case 7: return sClientNm;
            case 8: return cGenderCd;
            case 9: return cCvilStat;
            case 10: return sCitizenx;
            case 11: return dBirthDte;
            case 12: return sBirthPlc;
            case 13: return sAddlInfo;
            case 14: return sSpouseID;
            case 15: return cLRClient;
            case 16: return cMCClient;
            case 17: return cSCClient;
            case 18: return cSPClient;
            case 19: return cCPClient;
            case 20: return cRecdStat;
            case 21: return dModified;
            case 22: return cSupplier;
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
            case 2: cClientTp = (String) foValue; break;
            case 3: sLastName = (String) foValue; break;
            case 4: sFrstName = (String) foValue; break;
            case 5: sMiddName = (String) foValue; break;
            case 6: sSuffixNm = (String) foValue; break;
            case 7: sClientNm = (String) foValue; break;
            case 8: cGenderCd = (String) foValue; break;
            case 9: cCvilStat = (String) foValue; break;
            case 10: sCitizenx = (String) foValue; break;
            case 11: dBirthDte = SQLUtil.toDate(String.valueOf(foValue), SQLUtil.FORMAT_SHORT_DATE); break;
            case 12: sBirthPlc = (String) foValue; break;
            case 13: sAddlInfo = (String) foValue; break;
            case 14: sSpouseID = (String) foValue; break;
            case 15: cLRClient = (String) foValue; break;
            case 16: cMCClient = (String) foValue; break;
            case 17: cSCClient = (String) foValue; break;
            case 18: cSPClient = (String) foValue; break;
            case 19: cCPClient = (String) foValue; break;
            case 20: cRecdStat = (String) foValue; break;
            case 21: dModified = (Date) foValue; break;
            case 22: cSupplier = (String) foValue; break;
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
            if (getColumn(i + 1).substring(0, 1).equals("d")){
                loJSON.put(laColumns.get(i), SQLUtil.dateFormat(getValue(getColumn(i + 1)), SQLUtil.FORMAT_TIMESTAMP));
            } else 
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
