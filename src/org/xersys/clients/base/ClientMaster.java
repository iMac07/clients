package org.xersys.clients.base;

import org.xersys.commander.iface.LRecordMas;
import com.mysql.jdbc.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;
import javax.sql.rowset.RowSetProvider;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.xersys.commander.contants.EditMode;
import org.xersys.commander.contants.RecordStatus;
import org.xersys.commander.iface.XNautilus;
import org.xersys.commander.iface.XRecord;
import org.xersys.commander.iface.XSearchRecord;
import org.xersys.commander.util.CommonUtil;
import org.xersys.commander.util.MiscUtil;
import org.xersys.commander.util.SQLUtil;
import org.xersys.commander.util.Temp_Transactions;
import org.xersys.parameters.search.ParameterSearchEngine;

public class ClientMaster implements XRecord, XSearchRecord{
    private final String SOURCE_CODE = "CLTx";
    
    private XNautilus p_oNautilus;
    private LRecordMas p_oListener;
    
    private String p_sBranchCd;
    private String p_sMessagex;
    private String p_sTmpTrans;
    
    private int p_nEditMode;
    private boolean p_bWithParent;
    private boolean p_bSaveToDisk;
    
    private CachedRowSet p_oClient;
    private CachedRowSet p_oMobile;
    private CachedRowSet p_oAddress;
    private CachedRowSet p_oMail;
    
    private ArrayList<Temp_Transactions> p_oTemp;
    
    private ParameterSearchEngine _search_param;
    
    public ClientMaster(XNautilus foNautilus, String fsBranchCd, boolean fbWithParent){
        p_oNautilus = foNautilus;
        p_sBranchCd = fsBranchCd;
        p_bWithParent = fbWithParent;
        
        _search_param = new ParameterSearchEngine(p_oNautilus);
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
        
        try {
            String lsSQL;
            ResultSet loRS;
            
            RowSetFactory factory = RowSetProvider.newFactory();
            
            //create empty master record
            lsSQL = MiscUtil.addCondition(getSQ_Master(), "0=1");
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oClient = factory.createCachedRowSet();
            p_oClient.populate(loRS);
            MiscUtil.close(loRS);
            addClientRow();
            
            //create empty mobile record
            lsSQL = MiscUtil.addCondition(getSQ_Mobile(), "0=1");
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oMobile = factory.createCachedRowSet();
            p_oMobile.populate(loRS);
            MiscUtil.close(loRS);
            addMobileRow();
            
            //create empty address record
            lsSQL = MiscUtil.addCondition(getSQ_Address(), "0=1");
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oAddress = factory.createCachedRowSet();
            p_oAddress.populate(loRS);
            MiscUtil.close(loRS);
            addAddressRow();
            
            //create empty address record
            lsSQL = MiscUtil.addCondition(getSQ_EMail(), "0=1");
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oMail = factory.createCachedRowSet();
            p_oMail.populate(loRS);
            MiscUtil.close(loRS);
            addEMailRow();
        } catch (SQLException ex) {
            setMessage(ex.getMessage());
            return false;
        }
        
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
            ex.printStackTrace();
            return lbLoad;
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
        System.out.println(this.getClass().getSimpleName() + ".SaveRecord()");
        
        if (p_nEditMode != EditMode.ADDNEW &&
            p_nEditMode != EditMode.UPDATE){
            System.err.println("Transaction is not on update mode.");
            return false;
        }
        
        if (!fbConfirmed){
            saveToDisk(RecordStatus.ACTIVE);
            return true;
        }
        
        String lsSQL = "";
        
        //if (!isEntryOK()) return false;

        try {
            if (!p_bWithParent) p_oNautilus.beginTrans();
        
            if ("".equals((String) getMaster("sClientID"))){ //new record
                Connection loConn = getConnection();

                p_oClient.updateObject("sClientID", MiscUtil.getNextCode("Client_Master", "sClientID", true, loConn, p_sBranchCd));
                p_oClient.updateObject("dModified", p_oNautilus.getServerDate());
                p_oClient.updateRow();
                
                if (!p_bWithParent) MiscUtil.close(loConn);

                //save mobile number
                p_oMobile.beforeFirst();
                while (p_oMobile.next()){
                    if (!"".equals((String) p_oMobile.getObject("sMobileNo"))){
                        p_oMobile.updateObject("sClientID", p_oClient.getObject("sClientID"));
                    
                        lsSQL = MiscUtil.rowset2SQL(p_oMobile, "Client_Mobile", "");

                        if(p_oNautilus.executeUpdate(lsSQL, "Client_Mobile", p_sBranchCd, "") <= 0){
                            if(!p_oNautilus.getMessage().isEmpty())
                                setMessage(p_oNautilus.getMessage());
                            else
                                setMessage("No record updated");

                            if (!p_bWithParent) p_oNautilus.rollbackTrans();
                            return false;
                        } 
                    }
                }
                
                //save email address
                p_oMail.beforeFirst();
                while (p_oMail.next()){
                    if (!"".equals((String) p_oMail.getObject("sEmailAdd"))){
                        p_oMail.updateObject("sClientID", p_oMail.getObject("sClientID"));
                    
                        lsSQL = MiscUtil.rowset2SQL(p_oMail, "Client_eMail_Address", "");

                        if(p_oNautilus.executeUpdate(lsSQL, "Client_eMail_Address", p_sBranchCd, "") <= 0){
                            if(!p_oNautilus.getMessage().isEmpty())
                                setMessage(p_oNautilus.getMessage());
                            else
                                setMessage("No record updated");

                            if (!p_bWithParent) p_oNautilus.rollbackTrans();
                            return false;
                        } 
                    }
                }
                
                lsSQL = MiscUtil.rowset2SQL(p_oClient, "Client_Master", "");
            } else { //old record
            }
            
            if (lsSQL.equals("")){
                if (!p_bWithParent) p_oNautilus.rollbackTrans();
                
                setMessage("No record to update");
                return false;
            }
            
            if(p_oNautilus.executeUpdate(lsSQL, "Client_Master", p_sBranchCd, "") <= 0){
                if(!p_oNautilus.getMessage().isEmpty())
                    setMessage(p_oNautilus.getMessage());
                else
                    setMessage("No record updated");
            } 
            
            saveToDisk(RecordStatus.INACTIVE);

            if (!p_bWithParent) {
                if(!p_oNautilus.getMessage().isEmpty())
                    p_oNautilus.rollbackTrans();
                else
                    p_oNautilus.commitTrans();
            }    
        } catch (SQLException ex) {
            if (!p_bWithParent) p_oNautilus.rollbackTrans();
            
            ex.printStackTrace();
            setMessage(ex.getMessage());
            return false;
        }
        
        p_nEditMode = EditMode.UNKNOWN;
        
        return true;
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
        JSONObject loJSON = new JSONObject();
        ParameterSearchEngine loParameter = new ParameterSearchEngine(p_oNautilus);
        
        if (_search_param == null){
            loJSON.put("result", "error");
            loJSON.put("message", "Parameter search object is not set.");
            return loJSON;
        }
        
        if (p_nEditMode != EditMode.ADDNEW &&
            p_nEditMode != EditMode.UPDATE){
            loJSON.put("result", "error");
            loJSON.put("message", "Invalid edit mode detected.");
            return loJSON;        
        }
        
        if (fsValue.isEmpty()){
            loJSON.put("result", "error");
            loJSON.put("message", "Search value must not be empty.");
            return loJSON;        
        }
        
        _search_param.setKey(fsKey);
        _search_param.setFilter(fsFilter);
        _search_param.sethMax(fnMaxRow);
        _search_param.setExact(fbExact);

        return _search_param.Search(foType, fsValue);
    }
    
    @Override
    public String getMessage() {
        return p_sMessagex;
    }

    @Override
    public void setListener(Object foListener) {
        p_oListener = (LRecordMas) foListener;
    }
    
    @Override
    public void setSaveToDisk(boolean fbValue) {
        p_bSaveToDisk = fbValue;
    }

    public void setMaster(String fsFieldNm, Object foValue) throws SQLException{
        if (p_nEditMode != EditMode.ADDNEW &&
            p_nEditMode != EditMode.UPDATE){
            System.err.println("Transaction is not on update mode.");
            return;
        }
        
        switch (fsFieldNm){
            case "xAddressx":
                p_oAddress = (CachedRowSet) foValue;
                break;
            case "xMobileNo":
                p_oMobile = (CachedRowSet) foValue;
                
                p_oMobile.first();
                p_oListener.MasterRetreive("xMobileNo", (String) p_oMobile.getObject("sMobileNo"));
                break;
            case "xEmailAdd":
                p_oMail = (CachedRowSet) foValue;
                
                p_oMail.first();
                p_oListener.MasterRetreive("xEmailAdd", (String) p_oMail.getObject("sEmailAdd"));
                break;
            case "cClientTp":
                p_oClient.first();
                p_oClient.updateObject(fsFieldNm, foValue);
                
                if (((String) foValue).equals("0")){
                    p_oClient.updateObject("sClientNm", 
                                            CommonUtil.nameFormat(
                                                    (String) p_oClient.getObject("sLastName"), 
                                                    (String) p_oClient.getObject("sFrstName"), 
                                                    (String) p_oClient.getObject("sMiddName"), 
                                                    (String) p_oClient.getObject("sSuffixNm")));
                }
                
                p_oClient.updateRow();

                p_oListener.MasterRetreive(fsFieldNm, p_oClient.getObject(fsFieldNm));
                p_oListener.MasterRetreive("sClientNm", p_oClient.getObject("sClientNm"));
                break;
            case "sLastName":
            case "sFrstName":
            case "sMiddName":
            case "sSuffixNm":
                p_oClient.first();
                p_oClient.updateObject(fsFieldNm, foValue);
                
                if (p_oClient.getString("cClientTp").equals("0")){
                    p_oClient.updateObject("sClientNm", 
                                            CommonUtil.nameFormat(
                                                    (String) p_oClient.getObject("sLastName"), 
                                                    (String) p_oClient.getObject("sFrstName"), 
                                                    (String) p_oClient.getObject("sMiddName"), 
                                                    (String) p_oClient.getObject("sSuffixNm")));
                }
                
                p_oClient.updateRow();

                p_oListener.MasterRetreive(fsFieldNm, p_oClient.getObject(fsFieldNm));
                p_oListener.MasterRetreive("sClientNm", p_oClient.getObject("sClientNm"));
                break;
            default:
                p_oClient.first();
                p_oClient.updateObject(fsFieldNm, foValue);
                p_oClient.updateRow();

                p_oListener.MasterRetreive(fsFieldNm, p_oClient.getObject(fsFieldNm));
        }
        
        saveToDisk(RecordStatus.ACTIVE);
    }

    public Object getMaster(String fsFieldNm) throws SQLException{
        p_oClient.first();
        switch (fsFieldNm){
            case "xMobileNo":
                p_oMobile.first();
                return (String) p_oMobile.getObject("sMobileNo");
            case "xAddressx":
                return "";
            default:
                return p_oClient.getObject(fsFieldNm);
        }
    }
    
    public CachedRowSet getMobile(){
        return p_oMobile;
    }
    
    public CachedRowSet getAddress(){
        return p_oAddress;
    }
    
    public CachedRowSet getEMail(){
        return p_oMail;
    }
    
    private void saveToDisk(String fsRecdStat){
        if (p_bSaveToDisk){
            String lsPayloadx = toJSONString();
            
            if (!lsPayloadx.isEmpty()){
                if (p_sTmpTrans.isEmpty()){
                    p_sTmpTrans = CommonUtil.getNextReference(p_oNautilus.getConnection().getConnection(), "xxxTempTransactions", "sOrderNox", "sSourceCd = " + SQLUtil.toSQL(SOURCE_CODE));
                    CommonUtil.saveTempOrder(p_oNautilus, SOURCE_CODE, p_sTmpTrans, lsPayloadx);
                } else
                    CommonUtil.saveTempOrder(p_oNautilus, SOURCE_CODE, p_sTmpTrans, lsPayloadx, fsRecdStat);
            }
        }
    }
    
    public boolean DeleteTempTransaction(Temp_Transactions foValue) {
        boolean lbSuccess =  CommonUtil.saveTempOrder(p_oNautilus, foValue.getSourceCode(), foValue.getOrderNo(), foValue.getPayload(), "0");
        loadTempTransactions();
        return lbSuccess;
    }
    
    private String toJSONString(){
        JSONParser loParser = new JSONParser();
        JSONArray laMaster = new JSONArray();
        JSONArray laAddress = new JSONArray();
        JSONArray laMobile = new JSONArray();
        JSONArray laEMail = new JSONArray();
        JSONObject loMaster;
        JSONObject loJSON;

        try {
            String lsValue = MiscUtil.RS2JSON(p_oClient).toJSONString();
            laMaster = (JSONArray) loParser.parse(lsValue);
            loMaster = (JSONObject) laMaster.get(0);
            
            lsValue = MiscUtil.RS2JSON(p_oAddress).toJSONString();
            laAddress = (JSONArray) loParser.parse(lsValue);
            
            lsValue = MiscUtil.RS2JSON(p_oMobile).toJSONString();
            laMobile = (JSONArray) loParser.parse(lsValue);
            
            lsValue = MiscUtil.RS2JSON(p_oMail).toJSONString();
            laEMail = (JSONArray) loParser.parse(lsValue);
 
            loJSON = new JSONObject();
            loJSON.put("master", loMaster);
            loJSON.put("address", laAddress);
            loJSON.put("mobile", laMobile);
            loJSON.put("email", laEMail);
            
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
        JSONArray laEmail;
        
        try {
            String lsSQL;
            ResultSet loRS;
            
            RowSetFactory factory = RowSetProvider.newFactory();
            
            //create empty master record
            lsSQL = MiscUtil.addCondition(getSQ_Master(), "0=1");
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oClient = factory.createCachedRowSet();
            p_oClient.populate(loRS);
            MiscUtil.close(loRS);
            
            //create empty mobile record
            lsSQL = MiscUtil.addCondition(getSQ_Mobile(), "0=1");
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oMobile = factory.createCachedRowSet();
            p_oMobile.populate(loRS);
            MiscUtil.close(loRS);
            
            //create empty email record
            lsSQL = MiscUtil.addCondition(getSQ_EMail(), "0=1");
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oMail = factory.createCachedRowSet();
            p_oMail.populate(loRS);
            MiscUtil.close(loRS);
            
            //create empty address record
            lsSQL = MiscUtil.addCondition(getSQ_Address(), "0=1");
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oAddress = factory.createCachedRowSet();
            p_oAddress.populate(loRS);
            MiscUtil.close(loRS);
            
            loJSON = (JSONObject) loParser.parse(fsPayloadx);
            loMaster = (JSONObject) loJSON.get("master");
            laAddress = (JSONArray) loJSON.get("address");
            laMobile = (JSONArray) loJSON.get("mobile");
            laEmail = (JSONArray) loJSON.get("email");
            
            int lnCtr;
            int lnRow;
            String key;
            Iterator iterator;

            lnRow = 1;
            addClientRow();
            for(iterator = loMaster.keySet().iterator(); iterator.hasNext();) {
                key = (String) iterator.next();
                p_oClient.absolute(lnRow);
                if (loMaster.get(key) != null){
                    switch(key){
                        case "dBirthDte":
                            p_oClient.updateObject(key, SQLUtil.toDate((String) loMaster.get(key), SQLUtil.FORMAT_SHORT_DATE));
                            break;
                        default:
                            p_oClient.updateObject(key, loMaster.get(key));
                    }

                    p_oClient.updateRow();
                }
            }
            
            lnRow = 1;
            for(lnCtr = 0; lnCtr <= laAddress.size()-1; lnCtr++){
                JSONObject loAddress = (JSONObject) laAddress.get(lnCtr);

                addAddressRow();
                for(iterator = loAddress.keySet().iterator(); iterator.hasNext();) {
                    key = (String) iterator.next();
                    p_oAddress.absolute(lnRow);
                    p_oAddress.updateObject(key, loAddress.get(key));
                    p_oAddress.updateRow();
                }
                lnRow++;
            }
            
            lnRow = 1;
            for(lnCtr = 0; lnCtr <= laMobile.size()-1; lnCtr++){
                JSONObject loMobile = (JSONObject) laMobile.get(lnCtr);
            
                addMobileRow();
                for(iterator = loMobile.keySet().iterator(); iterator.hasNext();) {
                    key = (String) iterator.next();
                    p_oMobile.absolute(lnRow);
                    
                    switch (key){
                        case "nEntryNox":
                        case "nPriority":
                            p_oMobile.updateObject(key, (int) (long) loMobile.get(key));
                            break;
                        default:
                            p_oMobile.updateObject(key, loMobile.get(key));
                    }
                    
                    p_oMobile.updateRow();
                }
                lnRow++;
            }      
            
            lnRow = 1;
            for(lnCtr = 0; lnCtr <= laEmail.size()-1; lnCtr++){
                JSONObject loMail = (JSONObject) laEmail.get(lnCtr);
            
                addEMailRow();
                for(iterator = loMail.keySet().iterator(); iterator.hasNext();) {
                    key = (String) iterator.next();
                    p_oMail.absolute(lnRow);
                    
                    switch (key){
                        case "nEntryNox":
                        case "nPriority":
                            p_oMail.updateObject(key, (int) (long) loMail.get(key));
                            break;
                        default:
                            p_oMail.updateObject(key, loMail.get(key));
                    }
                    
                    p_oMail.updateRow();
                }
                lnRow++;
            }   
        } catch (SQLException | ParseException ex) {
            setMessage(ex.getMessage());
            ex.printStackTrace();
            return false;
        }
        
        return true;
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
        p_oTemp = CommonUtil.loadTempTransactions(p_oNautilus, SOURCE_CODE);
    }
    
    private String getSQ_Master(){
        return "SELECT" +
                    "  a.sClientID" +
                    ", a.cClientTp" +
                    ", a.sLastName" +
                    ", a.sFrstName" +
                    ", a.sMiddName" +
                    ", a.sSuffixNm" +
                    ", a.sClientNm" +
                    ", a.cGenderCd" +
                    ", a.cCvilStat" +
                    ", a.sCitizenx" +
                    ", a.dBirthDte" +
                    ", a.sBirthPlc" +
                    ", a.sAddlInfo" +
                    ", a.sSpouseID" +
                    ", a.cCustomer" +
                    ", a.cSupplier" +
                    ", a.cRecdStat" +
                    ", a.dModified" +
                " FROM Client_Master a";
    }
    
    private String getSQ_Mobile(){
        return "SELECT" +
                    "  sClientID" +
                    ", nEntryNox" +
                    ", sMobileNo" +
                    ", nPriority" +
                    ", cIncdMktg" +
                    ", cRecdStat" +
                " FROM Client_Mobile" +
                " ORDER BY nPriority";
    }
    
    private String getSQ_EMail(){
        return "SELECT" +
                    "  sClientID" +
                    ", nEntryNox" +
                    ", sEmailAdd" +
                    ", nPriority" +
                    ", cRecdStat" +
                " FROM Client_eMail_Address" +
                " ORDER BY nPriority";
    }
    
    private String getSQ_Address(){
        return "SELECT" +
                    "  a.sClientID" +
                    ", a.nEntryNox" +	
                    ", a.sHouseNox" +
                    ", a.sAddressx" +
                    ", a.sBrgyIDxx" +
                    ", a.sTownIDxx" +
                    ", a.nPriority" +
                    ", a.nLatitude" +
                    ", a.nLongitud" +
                    ", a.cPrimaryx" +
                    ", a.cRecdStat" +
                " FROM Client_Address a" +
                    " LEFT JOIN TownCity b ON a.sTownIDxx = b.sTownIDxx" +
                " ORDER BY a.nPriority";
    }
    
    private void addClientRow() throws SQLException{
        p_oClient.last();
        p_oClient.moveToInsertRow();
        
        MiscUtil.initRowSet(p_oClient);
        p_oClient.updateObject("cClientTp", "0");
        p_oClient.updateObject("cCvilStat", "0");
        p_oClient.updateObject("cGenderCd", "0");
        p_oClient.updateObject("dBirthDte", p_oNautilus.getServerDate());
        p_oClient.updateObject("cCustomer", "0");
        p_oClient.updateObject("cSupplier", "0");
        p_oClient.updateObject("cRecdStat", RecordStatus.ACTIVE);
        
        p_oClient.insertRow();
        p_oClient.moveToCurrentRow();
    }
    
    private void addMobileRow() throws SQLException{
        p_oMobile.last();
        p_oMobile.moveToInsertRow();
        
        MiscUtil.initRowSet(p_oMobile);
        p_oMobile.updateObject("nEntryNox", p_oMobile.size() + 1);
        p_oMobile.updateObject("nPriority", p_oMobile.size() + 1);
        p_oMobile.updateObject("cIncdMktg", "1");
        p_oMobile.updateObject("cRecdStat", "1");
        
        p_oMobile.insertRow();
        p_oMobile.moveToCurrentRow();
    }
    
    private void addEMailRow() throws SQLException{
        p_oMail.last();
        p_oMail.moveToInsertRow();
        
        MiscUtil.initRowSet(p_oMail);
        p_oMail.updateObject("nEntryNox", p_oMail.size() + 1);
        p_oMail.updateObject("nPriority", p_oMail.size() + 1);
        p_oMail.updateObject("cRecdStat", "1");
        
        p_oMail.insertRow();
        p_oMail.moveToCurrentRow();
    }
    
    private void addAddressRow() throws SQLException{
        p_oAddress.last();
        p_oAddress.moveToInsertRow();
        
        MiscUtil.initRowSet(p_oAddress);
        
        p_oAddress.insertRow();
        p_oAddress.moveToCurrentRow();
    }

    @Override
    public JSONObject SearchRecord(String fsValue, String fsKey, String fsFilter, int fnMaxRow, boolean fbExact) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
