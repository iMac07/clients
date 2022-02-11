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
import org.xersys.clients.search.ClientSearch;
import org.xersys.commander.contants.EditMode;
import org.xersys.commander.contants.RecordStatus;
import org.xersys.commander.iface.XNautilus;
import org.xersys.commander.iface.XRecord;
import org.xersys.commander.util.CommonUtil;
import org.xersys.commander.util.MiscUtil;
import org.xersys.commander.util.SQLUtil;
import org.xersys.commander.util.Temp_Transactions;
import org.xersys.parameters.search.ParamSearchF;

public class ClientMaster implements XRecord{
    private final String SOURCE_CODE = "CLTx";
    
    private final XNautilus p_oNautilus;
    private final boolean p_bWithParent;
    private final String p_sBranchCd;
    
    private LRecordMas p_oListener;
    private boolean p_bSaveToDisk;
    private String p_sMessagex;
    private String p_sTmpTrans;
    private int p_nEditMode;
    
    private CachedRowSet p_oClient;
    private CachedRowSet p_oMobile;
    private CachedRowSet p_oAddress;
    private CachedRowSet p_oMail;
    
    private final ParamSearchF p_oCountry;
    private final ParamSearchF p_oTownCity;
    private final ClientSearch p_oRecord;
    
    private ArrayList<Temp_Transactions> p_oTemp;
    
    public ClientMaster(XNautilus foNautilus, String fsBranchCd, boolean fbWithParent){
        p_oNautilus = foNautilus;
        p_sBranchCd = fsBranchCd;
        p_bWithParent = fbWithParent;
        
        p_oCountry = new ParamSearchF(p_oNautilus, ParamSearchF.SearchType.searchCountry);
        p_oTownCity = new ParamSearchF(p_oNautilus, ParamSearchF.SearchType.searchTownCity);
        p_oRecord = new ClientSearch(foNautilus, ClientSearch.SearchType.searchClient);
        
        loadTempTransactions();
        
         p_nEditMode = EditMode.UNKNOWN;
    }
    
    @Override
    public int getEditMode() {
        return p_nEditMode;
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
        p_nEditMode = EditMode.UPDATE;
        
        loadTempTransactions();
        
        return lbLoad;
    }

    @Override
    public boolean SaveRecord() {
        System.out.println(this.getClass().getSimpleName() + ".SaveRecord()");
        
        if (p_nEditMode != EditMode.ADDNEW &&
            p_nEditMode != EditMode.UPDATE){
            System.err.println("Transaction is not on update mode.");
            return false;
        }
        
        String lsSQL = "";
        
        if (!isEntryOK()) return false;

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
                        p_oMail.updateObject("sClientID", p_oClient.getObject("sClientID"));
                    
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
                
                //save address
                p_oAddress.beforeFirst();
                while (p_oAddress.next()){
                    if (!"".equals((String) p_oAddress.getObject("sAddressx")) ||
                        !"".equals((String) p_oAddress.getObject("sBrgyIDxx")) ||
                        !"".equals((String) p_oAddress.getObject("sTownIDxx"))){
                        p_oAddress.updateObject("sClientID", p_oClient.getObject("sClientID"));
                    
                        lsSQL = MiscUtil.rowset2SQL(p_oAddress, "Client_Address", "xBrgyName;xTownName;xProvName");

                        if(p_oNautilus.executeUpdate(lsSQL, "Client_Address", p_sBranchCd, "xBrgyName;xTownName;xProvName") <= 0){
                            if(!p_oNautilus.getMessage().isEmpty())
                                setMessage(p_oNautilus.getMessage());
                            else
                                setMessage("No record updated");

                            if (!p_bWithParent) p_oNautilus.rollbackTrans();
                            return false;
                        } 
                    }
                }
                
                lsSQL = MiscUtil.rowset2SQL(p_oClient, "Client_Master", "sCntryNme;sTownName");
            } else { //old record
                p_oClient.updateObject("dModified", p_oNautilus.getServerDate());
                p_oClient.updateRow();
                
                lsSQL = MiscUtil.rowset2SQL(p_oClient, "Client_Master", "sCntryNme;sTownName", "sClientID = " + SQLUtil.toSQL((String) getMaster("sClientID")));
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
            
            RemoveTempTranssaction();

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
        
        loadTempTransactions();
        p_nEditMode = EditMode.UNKNOWN;
        
        return true;
    }

    @Override
    public boolean UpdateRecord() {
         System.out.println(this.getClass().getSimpleName() + ".UpdateRecord()");
        
        if (p_nEditMode != EditMode.READY){
            setMessage("No record to update.");
            return false;
        }
        
        p_nEditMode = EditMode.UPDATE;
        
        return true;
    }

    @Override
    public boolean OpenRecord(String fsTransNox) {
        System.out.println(this.getClass().getSimpleName() + ".OpenRecord(String fsTransNox)");
        setMessage("");
        
        if (p_oNautilus == null){
            p_sMessagex = "Application driver is not set.";
            return false;
        }
        
        try {
            if (p_oClient != null){
                p_oClient.first();

                if (p_oClient.getString("sClientID").equals(fsTransNox)){
                    p_nEditMode  = EditMode.READY;
                    return true;
                }
            }
            
            String lsSQL;
            ResultSet loRS;
            
            RowSetFactory factory = RowSetProvider.newFactory();
            
            //open master record
            lsSQL = MiscUtil.addCondition(getSQ_Master(), "a.sClientID = " + SQLUtil.toSQL(fsTransNox));
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oClient = factory.createCachedRowSet();
            p_oClient.populate(loRS);
            MiscUtil.close(loRS);
            
            lsSQL = MiscUtil.addCondition(getSQ_Mobile(), "sClientID = " + SQLUtil.toSQL(fsTransNox));
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oMobile = factory.createCachedRowSet();
            p_oMobile.populate(loRS);
            MiscUtil.close(loRS);
            
            if (p_oMobile.size() == 0) addMobileRow();
            
            lsSQL = MiscUtil.addCondition(getSQ_EMail(), "sClientID = " + SQLUtil.toSQL(fsTransNox));
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oMail = factory.createCachedRowSet();
            p_oMail.populate(loRS);
            MiscUtil.close(loRS);
            
            if (p_oMail.size() == 0) addEMailRow();

            lsSQL = MiscUtil.addCondition(getSQ_Address(), "a.sClientID = " + SQLUtil.toSQL(fsTransNox));
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oAddress = factory.createCachedRowSet();
            p_oAddress.populate(loRS);
            MiscUtil.close(loRS);
            
            if (p_oAddress.size() == 0) addAddressRow();
            
            if (p_oClient.size() == 1) {                            
                p_nEditMode  = EditMode.READY;
                return true;
            }
            
            p_oClient = null;
            p_oMobile = null;
            p_oAddress = null;
            p_oMail = null;
            setMessage("No transction loaded.");
        } catch (SQLException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
        }
        
        p_nEditMode  = EditMode.UNKNOWN;
        return false;
    }

    @Override
    public boolean DeleteRecord(String fsTransNox) {
        return false;
    }

    @Override
    public boolean DeactivateRecord(String fsTransNox) {
        return false;
    }

    @Override
    public boolean ActivateRecord(String fsTransNox) {
        return false;
    }
    
    @Override
    public String getMessage() {
        return p_sMessagex;
    }

    @Override
    public void setListener(Object foListener) {
        p_oListener = (LRecordMas) foListener;
    }
    
    public void setSaveToDisk(boolean fbValue) {
        p_bSaveToDisk = fbValue;
    }

    @Override
    public void setMaster(int fnIndex, Object foValue) {
        String lsProcName = this.getClass().getSimpleName() + ".setMaster()";
        
        if (p_nEditMode != EditMode.ADDNEW &&
            p_nEditMode != EditMode.UPDATE){
            return;
        }
        
        try {
            switch (fnIndex){
                case 102: //xAddressx
                    p_oAddress = (CachedRowSet) foValue;
                    
                    p_oAddress.first();
                    String lsAddress = (String) p_oAddress.getObject("sHouseNox") + " " + 
                                        (String) p_oAddress.getObject("sAddressx");
                    
                    if (!"".equals((String) p_oAddress.getObject("xBrgyName"))){
                        lsAddress += " " + (String) p_oAddress.getObject("xBrgyName");
                    }
                    
                    if (!"".equals((String) p_oAddress.getObject("xTownName"))){
                        lsAddress += " " + (String) p_oAddress.getObject("xTownName") + ", " +
                                        (String) p_oAddress.getObject("xProvName");
                    }

                    p_oListener.MasterRetreive("xAddressx", lsAddress);
                    break;
                case 101: //xMobileNo
                    p_oMobile = (CachedRowSet) foValue;

                    p_oMobile.first();
                    p_oListener.MasterRetreive("xMobileNo", (String) p_oMobile.getObject("sMobileNo"));
                    break;
                case 103: //xEmailAdd
                    p_oMail = (CachedRowSet) foValue;

                    p_oMail.first();
                    p_oListener.MasterRetreive("xEmailAdd", (String) p_oMail.getObject("sEmailAdd"));
                    break;
                case 2: //cClientTp
                    p_oClient.first();
                    p_oClient.updateObject(fnIndex, foValue);

                    if (((String) foValue).equals("0")){
                        p_oClient.updateObject("sClientNm", 
                                                CommonUtil.nameFormat(
                                                        (String) p_oClient.getObject("sLastName"), 
                                                        (String) p_oClient.getObject("sFrstName"), 
                                                        (String) p_oClient.getObject("sMiddName"), 
                                                        (String) p_oClient.getObject("sSuffixNm")));
                    }

                    p_oClient.updateRow();

                    p_oListener.MasterRetreive(fnIndex, p_oClient.getObject(fnIndex));
                    p_oListener.MasterRetreive("sClientNm", p_oClient.getObject("sClientNm"));
                    break;
                case 3: //sLastName
                case 4: //sFrstName
                case 5: //sMiddName
                case 6: //sSuffixNm
                    p_oClient.first();
                    p_oClient.updateObject(fnIndex, foValue);

                    if (p_oClient.getString("cClientTp").equals("0")){
                        p_oClient.updateObject("sClientNm", 
                                                CommonUtil.nameFormat(
                                                        (String) p_oClient.getObject("sLastName"), 
                                                        (String) p_oClient.getObject("sFrstName"), 
                                                        (String) p_oClient.getObject("sMiddName"), 
                                                        (String) p_oClient.getObject("sSuffixNm")));
                    }

                    p_oClient.updateRow();

                    p_oListener.MasterRetreive(fnIndex, p_oClient.getObject(fnIndex));
                    p_oListener.MasterRetreive("sClientNm", p_oClient.getObject("sClientNm"));
                    break;
                case 10: //sCitizenx
                    getCountry((String) foValue);
                    break;
                case 12: //sBirthPlc
                    getBirthPlace((String) foValue);
                    break;
                default:
                    p_oClient.first();
                    p_oClient.updateObject(fnIndex, foValue);
                    p_oClient.updateRow();

                    p_oListener.MasterRetreive(fnIndex, p_oClient.getObject(fnIndex));
            }

            saveToDisk(RecordStatus.ACTIVE);
        } catch (SQLException e) {
            e.printStackTrace();
            setMessage("SQLException on " + lsProcName + ". Please inform your System Admin.");
        }
    }
    
    @Override
    public void setMaster(String fsFieldNm, Object foValue){
        try {
            switch (fsFieldNm){
                case "xMobileNo":
                    setMaster(101, foValue);
                    break;
                case "xAddressx":
                    setMaster(102, foValue);
                    break;
                case "xEmailAdd":
                    setMaster(103, foValue);
                    break;
                default:
                    setMaster(MiscUtil.getColumnIndex(p_oClient, fsFieldNm), foValue);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }        
    }
    
    @Override
    public Object getMaster(int fnIndex) {
        try {
            p_oClient.first();
            switch (fnIndex){
                case 101: //xMobileNo
                    p_oMobile.first();
                    return (String) p_oMobile.getObject("sMobileNo");
                case 102: //xAddressx
                    p_oAddress.first();
                    String lsAddress = (String) p_oAddress.getObject("sHouseNox") + " " + 
                                        (String) p_oAddress.getObject("sAddressx");
                    
                    if (!"".equals((String) p_oAddress.getObject("xBrgyName"))){
                        lsAddress += " " + (String) p_oAddress.getObject("xBrgyName");
                    }
                    
                    if (!"".equals((String) p_oAddress.getObject("xTownName"))){
                        lsAddress += " " + (String) p_oAddress.getObject("xTownName") + ", " +
                                        (String) p_oAddress.getObject("xProvName");
                    }
                    
                    return lsAddress.trim();
                case 103: //xEmailAdd
                    p_oMail.first();
                    return (String) p_oMail.getObject("sEmailAdd");
                default:
                    return p_oClient.getObject(fnIndex);
            }
        } catch (SQLException e) {
            return null;
        }
    }

    @Override
    public Object getMaster(String fsFieldNm){
        try {
            switch (fsFieldNm){
                case "xMobileNo":
                    return getMaster(101);
                case "xAddressx":
                    return getMaster(102);
                case "xEmailAdd":
                    return getMaster(103);
                default:
                    return getMaster(MiscUtil.getColumnIndex(p_oClient, fsFieldNm));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
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
        
        p_nEditMode = EditMode.UNKNOWN;
        return lbSuccess;
    }
    
    public JSONObject searchCountry(String fsKey, Object foValue, String fsFilter, String fsValue, boolean fbExact){
        p_oCountry.setKey(fsKey);
        p_oCountry.setValue(foValue);
        p_oCountry.setExact(fbExact);
        
        return p_oCountry.Search();
    }
    
    public ParamSearchF getSearchCountry(){
        return p_oCountry;
    }
    
    public JSONObject searchTownCity(String fsKey, Object foValue, String fsFilter, String fsValue, boolean fbExact){
        p_oTownCity.setKey(fsKey);
        p_oTownCity.setValue(foValue);
        p_oTownCity.setExact(fbExact);
        
        return p_oTownCity.Search();
    }
    
    public ParamSearchF getSearchTownCity(){
        return p_oTownCity;
    }
    
    public JSONObject searchRecord(String fsKey, Object foValue, boolean fbExact){
        p_oRecord.setKey(fsKey);
        p_oRecord.setValue(foValue);
        p_oRecord.setExact(fbExact);
        
        return p_oRecord.Search();
    }
    
    public ClientSearch getSearchRecord(){
        return p_oRecord;
    }
    
    private void getCountry(String foValue){
        String lsProcName = this.getClass().getSimpleName() + ".getCountry()";
        
        JSONObject loJSON = searchCountry("sCntryCde", foValue, "", "", true);
        if ("success".equals((String) loJSON.get("result"))){
            try {
                JSONParser loParser = new JSONParser();

                p_oClient.first();
                try {
                    JSONArray loArray = (JSONArray) loParser.parse((String) loJSON.get("payload"));

                    switch (loArray.size()){
                        case 0:
                            p_oClient.updateObject("sCntryCde", "");
                            p_oClient.updateObject("sCntryNme", "");
                            p_oClient.updateRow();
                            break;
                        default:
                            loJSON = (JSONObject) loArray.get(0);
                            p_oClient.updateObject("sCitizenx", (String) loJSON.get("sCntryCde"));
                            p_oClient.updateObject("sCntryNme", (String) loJSON.get("sCntryNme"));
                            p_oClient.updateRow();
                    }
                } catch (ParseException ex) {
                    ex.printStackTrace();
                    p_oListener.MasterRetreive("sCitizenx", "");
                    p_oListener.MasterRetreive("sCntryNme", "");
                    p_oClient.updateRow();
                }

                p_oListener.MasterRetreive("sCitizenx", (String) p_oClient.getObject("sCitizenx"));
                p_oListener.MasterRetreive("sCntryNme", (String) p_oClient.getObject("sCntryNme"));
            } catch (SQLException ex) {
                ex.printStackTrace();
                setMessage("SQLException on " + lsProcName + ". Please inform your System Admin.");
            }
        }
    }
    
    private void getBirthPlace(String foValue){
        String lsProcName = this.getClass().getSimpleName() + ".getBirthPlace()";
        
        JSONObject loJSON = searchTownCity("sTownIDxx", foValue, "", "", true);
        if ("success".equals((String) loJSON.get("result"))){
            try {
                JSONParser loParser = new JSONParser();
                
                p_oClient.first();
                try {
                    JSONArray loArray = (JSONArray) loParser.parse((String) loJSON.get("payload"));

                    switch (loArray.size()){
                        case 0:
                            p_oClient.updateObject("sBirthPlc", "");
                            p_oClient.updateObject("sTownName", "");
                            p_oClient.updateRow();
                            break;
                        default:
                            loJSON = (JSONObject) loArray.get(0);
                            p_oClient.updateObject("sBirthPlc", (String) loJSON.get("sTownIDxx"));
                            p_oClient.updateObject("sTownName", (String) loJSON.get("sTownName"));
                            p_oClient.updateRow();
                    }
                } catch (ParseException ex) {
                    ex.printStackTrace();
                    p_oListener.MasterRetreive("sBirthPlc", "");
                    p_oListener.MasterRetreive("sTownName", "");
                    p_oClient.updateRow();
                }

                p_oListener.MasterRetreive("sBirthPlc", (String) p_oClient.getObject("sBirthPlc"));
                p_oListener.MasterRetreive("sTownName", (String) p_oClient.getObject("sTownName"));
            } catch (SQLException ex) {
                ex.printStackTrace();
                setMessage("SQLException on " + lsProcName + ". Please inform your System Admin.");
            }
        }
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
            String lsValue = MiscUtil.RS2JSONi(p_oClient).toJSONString();
            laMaster = (JSONArray) loParser.parse(lsValue);
            loMaster = (JSONObject) laMaster.get(0);
            
            lsValue = MiscUtil.RS2JSONi(p_oAddress).toJSONString();
            laAddress = (JSONArray) loParser.parse(lsValue);
            
            lsValue = MiscUtil.RS2JSONi(p_oMobile).toJSONString();
            laMobile = (JSONArray) loParser.parse(lsValue);
            
            lsValue = MiscUtil.RS2JSONi(p_oMail).toJSONString();
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
            int lnKey;
            String lsKey;
            String lsIndex;
            Iterator iterator;

            lnRow = 1;
            addClientRow();
            for(iterator = loMaster.keySet().iterator(); iterator.hasNext();) {
                lsIndex = (String) iterator.next(); //string value of int
                lnKey = Integer.valueOf(lsIndex); //string to in
                lsKey = p_oClient.getMetaData().getColumnLabel(lnKey); //int to metadata
                p_oClient.absolute(lnRow);
                if (loMaster.get(lsIndex) != null){
                    switch(lsKey){
                        case "dBirthDte":
                            p_oClient.updateObject(lnKey, SQLUtil.toDate((String) loMaster.get(lsIndex), SQLUtil.FORMAT_SHORT_DATE));
                            break;
                        default:
                            p_oClient.updateObject(lnKey, loMaster.get(lsIndex));
                    }

                    p_oClient.updateRow();
                }
            }
            
            lnRow = 1;
            for(lnCtr = 0; lnCtr <= laAddress.size()-1; lnCtr++){
                JSONObject loAddress = (JSONObject) laAddress.get(lnCtr);

                addAddressRow();
                for(iterator = loAddress.keySet().iterator(); iterator.hasNext();) {
                    lsIndex = (String) iterator.next(); //string value of int
                    lnKey = Integer.valueOf(lsIndex); //string to in
                    p_oAddress.absolute(lnRow);
                    p_oAddress.updateObject(lnKey, loAddress.get(lsIndex));
                    p_oAddress.updateRow();
                }
                lnRow++;
            }
            
            lnRow = 1;
            for(lnCtr = 0; lnCtr <= laMobile.size()-1; lnCtr++){
                JSONObject loMobile = (JSONObject) laMobile.get(lnCtr);
            
                addMobileRow();
                for(iterator = loMobile.keySet().iterator(); iterator.hasNext();) {
                    lsIndex = (String) iterator.next(); //string value of int
                    lnKey = Integer.valueOf(lsIndex); //string to in
                    lsKey = p_oMobile.getMetaData().getColumnLabel(lnKey); //int to metadata
                    p_oMobile.absolute(lnRow);
                    
                    switch (lsKey){
                        case "nEntryNox":
                        case "nPriority":
                            p_oMobile.updateObject(lnKey, (int) (long) loMobile.get(lsIndex));
                            break;
                        default:
                            p_oMobile.updateObject(lnKey, loMobile.get(lsIndex));
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
                    lsIndex = (String) iterator.next(); //string value of int
                    lnKey = Integer.valueOf(lsIndex); //string to in
                    lsKey = p_oMail.getMetaData().getColumnLabel(lnKey); //int to metadata
                    p_oMail.absolute(lnRow);
                    
                    switch (lsKey){
                        case "nEntryNox":
                        case "nPriority":
                            p_oMail.updateObject(lnKey, (int) (long) loMail.get(lsIndex));
                            break;
                        default:
                            p_oMail.updateObject(lnKey, loMail.get(lsIndex));
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
    
    public ArrayList<Temp_Transactions> TempTransactions() {
        return p_oTemp;
    }
    
    public void RemoveTempTranssaction(){
        saveToDisk(RecordStatus.UNKNOWN);
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
                    ", a.cMechanic" +
                    ", a.cSrvcAdvs" +
                    ", a.cRecdStat" +
                    ", a.dModified" +
                    ", b.sCntryNme" +
                    ", c.sTownName" +
                " FROM Client_Master a" +
                    " LEFT JOIN Country b ON a.sCitizenx = b.sCntryCde" +
                    " LEFT JOIN TownCity c ON a.sBirthPlc = c.sTownIDxx";
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
                    ", IFNULL(b.sBrgyName, '') xBrgyName" +
                    ", IFNULL(c.sTownName, '') xTownName" +
                    ", IFNULL(d.sProvName, '') xProvName" +
                " FROM Client_Address a" +
                    " LEFT JOIN Barangay b ON a.sBrgyIDxx = b.sBrgyIDxx" +
                    " LEFT JOIN TownCity c ON a.sTownIDxx = c.sTownIDxx" +        
                    " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx" +
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
        p_oClient.updateObject("cMechanic", "0");
        p_oClient.updateObject("cSrvcAdvs", "0");
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
        p_oAddress.updateObject("nEntryNox", p_oAddress.size() + 1);
        p_oAddress.updateObject("nPriority", p_oAddress.size() + 1);
        p_oAddress.updateObject("cPrimaryx", "1");
        p_oAddress.updateObject("cRecdStat", "1");
        
        p_oAddress.insertRow();
        p_oAddress.moveToCurrentRow();
    }
    
    private boolean isEntryOK(){
        try {
            //assign values to master record
            p_oClient.first();
            
            if (((String)getMaster("sLastName")).equals("") ||
                ((String)getMaster("sFrstName")).equals("")) {
                setMessage("Client first name or last name must not be empty.");
                return false;
            }           

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            setMessage(e.getMessage());
            return false;
        }
    }
}
