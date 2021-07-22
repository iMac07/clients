package org.xersys.clients.base;

import com.mysql.jdbc.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.xersys.clients.pojo.Client_Address;
import org.xersys.clients.pojo.Client_Master;
import org.xersys.clients.pojo.Client_Mobile;
import org.xersys.commander.contants.EditMode;
import org.xersys.commander.contants.RecordStatus;
import org.xersys.commander.iface.XNautilus;
import org.xersys.commander.iface.XRecord;
import org.xersys.commander.iface.XSearchTran;
import org.xersys.commander.util.CommonUtil;
import org.xersys.commander.util.MiscUtil;
import org.xersys.commander.util.SQLUtil;
import org.xersys.lib.pojo.Temp_Transactions;

public class Clients implements XRecord, XSearchTran{
    private final String SOURCE_CODE = "CLTx";
    
    private XNautilus p_oNautilus;
    private LClients p_oListener;
    
    private String p_sBranchCd;
    private String p_sMessagex;
    private String p_sTmpTrans;
    
    private int p_nEditMode;
    private boolean p_bWithParent;
    private boolean p_bSaveToDisk;
    
    private Client_Master p_oClient;
    private ArrayList<Client_Mobile> p_oMobile;
    private ArrayList<Client_Address> p_oAddress;
    private ArrayList<Temp_Transactions> p_oTemp;
    
    public Clients(XNautilus foNautilus, String fsBranchCd, boolean fbWithParent){
        p_oNautilus = foNautilus;
        p_sBranchCd = fsBranchCd;
        p_bWithParent = fbWithParent;
        
        loadTempTransactions();
    }
    
    @Override
    public boolean NewRecord() {
        System.out.println(this.getClass().getSimpleName() + ".NewRecord()");
        
        if (p_oNautilus == null){
            p_sMessagex = "Application driver is not set.";
            return false;
        }
        
        p_sTmpTrans = "";
        
        p_oClient = new Client_Master();
        p_oAddress = new ArrayList<>();
        p_oMobile = new ArrayList<>();
        
        //add others
        saveToDisk(RecordStatus.ACTIVE);
        
        loadTempTransactions();
        p_nEditMode = EditMode.ADDNEW;
        
        return true;
    }
    
    @Override
    public boolean NewRecord(String fsTmpTrans) {
        System.out.println(this.getClass().getSimpleName() + ".NewRecord(String fsTmpTrans)");
        
        if (fsTmpTrans.isEmpty()) return NewRecord();
        
        ResultSet loTran = null;
        boolean lbLoad = false;
        
        try {
            loTran = CommonUtil.getTempOrder(p_oNautilus, SOURCE_CODE, fsTmpTrans);
            
            if (loTran.next()){
                lbLoad = toDTO(loTran.getString("sPayloadx"));
            }
        } catch (SQLException ex) {
            setMessage(ex.getMessage());
            lbLoad = false;
        } finally {
            MiscUtil.close(loTran);
        }
        
        p_sTmpTrans = fsTmpTrans;
        p_nEditMode = EditMode.ADDNEW;
        
        loadTempTransactions();
        
        return lbLoad;
    }

    @Override
    public boolean SaveRecord(boolean fbConfirmed) {
        return false;
    }

    @Override
    public boolean UpdateRecord() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean OpenRecord(String fsTransNox) {
        if (p_oNautilus == null){
            p_sMessagex = "Application driver is not set.";
            return false;
        }
        
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean DeleteRecord(String fsTransNox) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean DeactivateRecord(String fsTransNox) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean ActivateRecord(String fsTransNox) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ArrayList<Temp_Transactions> TempTransactions() {
        return p_oTemp;
    }
    
    @Override
    public JSONObject Search(Enum foType, String fsValue, String fsKey, String fsFilter, int fnMaxRow, boolean fbExact) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public String getMessage() {
        return p_sMessagex;
    }

    @Override
    public void setListener(Object foListener) {
        p_oListener = (LClients) foListener;
    }
    
    @Override
    public void setSaveToDisk(boolean fbValue) {
        p_bSaveToDisk = fbValue;
    }

    @Override
    public void setMaster(String fsFieldNm, Object foValue) {
        if (p_nEditMode != EditMode.ADDNEW &&
            p_nEditMode != EditMode.UPDATE){
            System.err.println("Transaction is not on update mode.");
            return;
        }
        
        p_oClient.setValue(fsFieldNm, foValue);
        p_oListener.MasterRetreive(fsFieldNm, p_oClient.getValue(fsFieldNm));
        
        saveToDisk(RecordStatus.ACTIVE);
    }

    @Override
    public Object getMaster(String fsFieldNm) {
        return p_oClient.getValue(fsFieldNm);
    }

    @Override
    public void setMaster(int fnIndex, Object foValue) {
        setMaster(p_oClient.getColumn(fnIndex), foValue);
    }

    @Override
    public Object getMaster(int fnIndex) {
        return getMaster(p_oClient.getColumn(fnIndex));
    }
    
    private void saveToDisk(String fsRecdStat){
        if (p_bSaveToDisk){
            String lsPayloadx = toJSONString();
            
            if (p_sTmpTrans.isEmpty()){
                p_sTmpTrans = CommonUtil.getNextReference(p_oNautilus.getConnection().getConnection(), "xxxTempTransactions", "sOrderNox", "sSourceCd = " + SQLUtil.toSQL(SOURCE_CODE));
                CommonUtil.saveTempOrder(p_oNautilus, SOURCE_CODE, p_sTmpTrans, lsPayloadx);
            } else
                CommonUtil.saveTempOrder(p_oNautilus, SOURCE_CODE, p_sTmpTrans, lsPayloadx, fsRecdStat);
        }
    }
    
    public boolean DeleteTempTransaction(Temp_Transactions foValue) {
        boolean lbSuccess =  CommonUtil.saveTempOrder(p_oNautilus, foValue.getSourceCode(), foValue.getOrderNo(), foValue.getPayload(), "0");
        loadTempTransactions();
        return lbSuccess;
    }
    
    private String toJSONString(){
        JSONParser loParser = new JSONParser();
        JSONArray loAddress = new JSONArray();
        JSONArray loMobile = new JSONArray();
        JSONObject loMaster;
        JSONObject loJSON;

        try {
            loMaster = (JSONObject) loParser.parse(p_oClient.toJSONString());

            for (int lnCtr = 0; lnCtr < p_oAddress.size(); lnCtr++){
                loJSON = (JSONObject) loParser.parse(p_oAddress.get(lnCtr).toJSONString());
                loAddress.add(loJSON);
            }
            
            for (int lnCtr = 0; lnCtr < p_oMobile.size(); lnCtr++){
                loJSON = (JSONObject) loParser.parse(p_oMobile.get(lnCtr).toJSONString());
                loMobile.add(loJSON);
            }

            loJSON = new JSONObject();
            loJSON.put("master", loMaster);
            loJSON.put("address", loAddress);
            loJSON.put("mobile", loMobile);
            
            return loJSON.toJSONString();
        } catch (ParseException ex) {
            ex.printStackTrace();
        }
        
        return "";
    }
    
    private boolean toDTO(String fsPayloadx){
        boolean lbLoad = false;
        
        if (fsPayloadx.isEmpty()) return lbLoad;
        
        JSONParser loParser = new JSONParser();
        
        JSONObject loJSON;
        JSONObject loMaster;
        JSONArray laAddress;
        JSONArray laMobile;
        
        
        p_oClient = new Client_Master();
        p_oAddress = new ArrayList<>();
        p_oMobile = new ArrayList<>();
        
        try {
            loJSON = (JSONObject) loParser.parse(fsPayloadx);
            loMaster = (JSONObject) loJSON.get("master");
            laAddress = (JSONArray) loJSON.get("address");
            laMobile = (JSONArray) loJSON.get("mobile");
        
            int lnCtr;
            String key;
            Iterator iterator;
            
            
            for(iterator = loMaster.keySet().iterator(); iterator.hasNext();) {
                key = (String) iterator.next();
                if (loMaster.get(key) != null)
                    p_oClient.setValue(key, loMaster.get(key));
            }
            
            JSONObject loAddress;
            Client_Address loClientAddress;
            
            for(lnCtr = 0; lnCtr <= laAddress.size()-1; lnCtr++){
                loClientAddress = new Client_Address();
                loAddress = (JSONObject) laAddress.get(lnCtr);
                
                for(iterator = loAddress.keySet().iterator(); iterator.hasNext();) {
                    key = (String) iterator.next();
                    loClientAddress.setValue(key, loAddress.get(key));
                }
                p_oAddress.add(loClientAddress);
            }
            
            JSONObject loMobile;
            Client_Mobile loClientMobile;
            
            for(lnCtr = 0; lnCtr <= laMobile.size()-1; lnCtr++){
                loClientMobile = new Client_Mobile();
                loMobile = (JSONObject) laMobile.get(lnCtr);
                
                for(iterator = loMobile.keySet().iterator(); iterator.hasNext();) {
                    key = (String) iterator.next();
                    loClientMobile.setValue(key, loMobile.get(key));
                }
                p_oMobile.add(loClientMobile);
            }
            
            lbLoad = true;
        } catch (ParseException ex) {
            setMessage(ex.getMessage());
        }
        
        return lbLoad;
    }
    
    private Connection getConnection(){         
        Connection foConn;
        
        if (p_bWithParent){
            foConn = (Connection) p_oNautilus.getConnection().getConnection();
            
            if (foConn == null) foConn = (Connection) p_oNautilus.doConnect();
        } else 
            foConn = (Connection) p_oNautilus.doConnect();
        
        return foConn;
    }
    
    private void setMessage(String fsValue){
        p_sMessagex = fsValue;
    }
    
    private void loadTempTransactions(){
        String lsSQL = "SELECT * FROM xxxTempTransactions" +
                        " WHERE cRecdStat = '1'" +
                            " AND sSourceCd = " + SQLUtil.toSQL(SOURCE_CODE);
        
        ResultSet loRS = p_oNautilus.executeQuery(lsSQL);
        
        Temp_Transactions loTemp;
        p_oTemp = new ArrayList<>();
        
        try {
            while(loRS.next()){
                loTemp = new Temp_Transactions();
                loTemp.setSourceCode(loRS.getString("sSourceCd"));
                loTemp.setOrderNo(loRS.getString("sOrderNox"));
                loTemp.setDateCreated(SQLUtil.toDate(loRS.getString("dCreatedx"), SQLUtil.FORMAT_TIMESTAMP));
                loTemp.setPayload(loRS.getString("sPayloadx"));
                p_oTemp.add(loTemp);
            }
        } catch (SQLException ex) {
            System.err.println(ex.getMessage());
        } finally {
            MiscUtil.close(loRS);
        }
    }
}
