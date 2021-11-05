package org.xersys.clients.base;

import org.xersys.commander.iface.LRecordMas;
import com.mysql.jdbc.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.xersys.commander.util.MiscUtil;
import org.xersys.commander.util.SQLUtil;
import org.xersys.commander.util.StringUtil;
import org.xersys.parameters.search.ParamSearchF;

public class APClient implements XRecord{    
    private final String MASTER_TABLE = "AP_Master";
    
    private final XNautilus p_oNautilus;
    private final boolean p_bWithParent;
    private final String p_sBranchCd;
    
    private final ParamSearchF p_oTerm;
    private final ClientSearch p_oClient;
    
    private LRecordMas p_oListener;    
    private String p_sMessagex;
    private int p_nEditMode;
    
    private CachedRowSet p_oAPClient;
    
    public APClient(XNautilus foNautilus, String fsBranchCd, boolean fbWithParent){
        p_oNautilus = foNautilus;
        p_sBranchCd = fsBranchCd;
        p_bWithParent = fbWithParent;
        
        p_oTerm = new ParamSearchF(p_oNautilus, ParamSearchF.SearchType.searchTerm);
        p_oClient = new ClientSearch(p_oNautilus, ClientSearch.SearchType.searchSupplier);
        
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
        
        try {
            String lsSQL;
            ResultSet loRS;
            
            RowSetFactory factory = RowSetProvider.newFactory();
            
            //create empty master record
            lsSQL = MiscUtil.addCondition(getSQ_Master(), "0=1");
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oAPClient = factory.createCachedRowSet();
            p_oAPClient.populate(loRS);
            MiscUtil.close(loRS);
            initMaster();

        } catch (SQLException ex) {
            setMessage(ex.getMessage());
            return false;
        }
        
        p_nEditMode = EditMode.ADDNEW;
        
        return true;
    }

    @Override
    public boolean SaveRecord() {
        System.out.println(this.getClass().getSimpleName() + ".SaveRecord()");
        
        setMessage("");
        
        if (p_nEditMode != EditMode.ADDNEW &&
            p_nEditMode != EditMode.UPDATE){
            System.err.println("Transaction is not on update mode.");
            return false;
        }
        
        String lsSQL = "";
        
        if (!isEntryOK()) return false;

        try {
            if (!p_bWithParent) p_oNautilus.beginTrans();
        
            if (p_nEditMode == EditMode.ADDNEW){
                Connection loConn = getConnection();
                
                if (!p_bWithParent) MiscUtil.close(loConn);
                
                lsSQL = MiscUtil.rowset2SQL(p_oAPClient, MASTER_TABLE, "xClientNm;xLastName;xFrstName;xMiddName;xAddressx;xTermName");
            } else { //old record
                lsSQL = MiscUtil.rowset2SQL(p_oAPClient, MASTER_TABLE, "xClientNm;xLastName;xFrstName;xMiddName;xAddressx;xTermName", "a.sClientID = " + SQLUtil.toSQL((String) getMaster("sClientID")));
            }
            
            if (lsSQL.equals("")){
                if (!p_bWithParent) p_oNautilus.rollbackTrans();
                
                setMessage("No record to update");
                return false;
            }
            
            if(p_oNautilus.executeUpdate(lsSQL, MASTER_TABLE, p_sBranchCd, "") <= 0){
                if(!p_oNautilus.getMessage().isEmpty())
                    setMessage(p_oNautilus.getMessage());
                else
                    setMessage("No record updated");
            } 

            if (!p_bWithParent) {
                if (!p_sMessagex.isEmpty())
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
        if (p_nEditMode != EditMode.READY) return false;
        
        p_nEditMode = EditMode.UPDATE;
        return true;
    }

    @Override
    public boolean OpenRecord(String fsClientID) {
        System.out.println(this.getClass().getSimpleName() + ".OpenRecord()");
        setMessage("");   
        
        if (p_oNautilus == null){
            p_sMessagex = "Application driver is not set.";
            return false;
        }
        
        try {
            if (p_oAPClient != null){
                p_oAPClient.first();

                if (p_oAPClient.getString("sStockIDx").equals(fsClientID)){
                    p_nEditMode  = EditMode.READY;
                    return true;
                }
            }
            
            String lsSQL;
            ResultSet loRS;
            
            RowSetFactory factory = RowSetProvider.newFactory();
            
            //open master record
            lsSQL = MiscUtil.addCondition(getSQ_Master(), 
                        " sClientID = " + SQLUtil.toSQL(fsClientID) +
                        " AND sBranchCd = " + SQLUtil.toSQL(p_sBranchCd));
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oAPClient = factory.createCachedRowSet();
            p_oAPClient.populate(loRS);
            MiscUtil.close(loRS);
            
            if (p_oAPClient.size() == 1) {                            
                p_nEditMode  = EditMode.READY;
                return true;
            }
            
            setMessage("No record loaded.");
        } catch (SQLException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
        }
        
        p_nEditMode  = EditMode.UNKNOWN;
        return false;
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
    public String getMessage() {
        return p_sMessagex;
    }

    @Override
    public void setListener(Object foListener) {
        p_oListener = (LRecordMas) foListener;
    }

    @Override
    public void setMaster(String fsFieldNm, Object foValue){
        String lsProcName = this.getClass().getSimpleName() + ".setMaster()";
        
        if (p_nEditMode != EditMode.ADDNEW &&
            p_nEditMode != EditMode.UPDATE){
            System.err.println("Transaction is not on update mode.");
            return;
        }
        
        try {
            switch (fsFieldNm){
                case "nDiscount":
                case "nCredLimt":
                case "nBalForwd":
                case "nOBalance":
                case "nABalance":
                    p_oAPClient.first();
                    
                    if (StringUtil.isNumeric(String.valueOf(foValue))){
                        p_oAPClient.updateObject(fsFieldNm, foValue);
                        p_oAPClient.updateRow();
                    }
                    
                    p_oListener.MasterRetreive(fsFieldNm, p_oAPClient.getObject(fsFieldNm));
                    break;
                case "dBalForwd":
                case "dCltSince":
                    p_oAPClient.first();
                    
                    if (StringUtil.isDate(fsFieldNm, SQLUtil.FORMAT_SHORT_DATE)){
                        p_oAPClient.updateObject(fsFieldNm, foValue);
                        p_oAPClient.updateRow();
                    }
                    
                    p_oListener.MasterRetreive(fsFieldNm, p_oAPClient.getObject(fsFieldNm));
                    break;
                case "sClientID":
                    getClient((String) foValue);
                    break;
                case "sTermCode":
                    getTerm((String) foValue);
                    break;
                default:
                    p_oAPClient.first();
                    p_oAPClient.updateObject(fsFieldNm, foValue);
                    p_oAPClient.updateRow();

                    p_oListener.MasterRetreive(fsFieldNm, p_oAPClient.getObject(fsFieldNm));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            setMessage("SQLException on " + lsProcName + ". Please inform your System Admin.");
        }
    }

    @Override
    public Object getMaster(String fsFieldNm){
        try {
            p_oAPClient.first();
            return p_oAPClient.getObject(fsFieldNm);
        } catch (SQLException e) {
            return null;
        }
    }
    
    public JSONObject searchTerm(String fsKey, Object foValue, String fsFilter, String fsValue, boolean fbExact){
        p_oTerm.setKey(fsKey);
        p_oTerm.setValue(foValue);
        p_oTerm.setExact(fbExact);
        
        return p_oTerm.Search();
    }
    
    public ParamSearchF getSearchTerm(){
        return p_oTerm;
    }
    
    private void getTerm(String foValue){
        String lsProcName = this.getClass().getSimpleName() + ".getTerm()";
        
        JSONObject loJSON = searchTerm("sTermCode", foValue, "", "", true);
        if ("success".equals((String) loJSON.get("result"))){
            try {
                JSONParser loParser = new JSONParser();

                p_oAPClient.first();
                try {
                    JSONArray loArray = (JSONArray) loParser.parse((String) loJSON.get("payload"));

                    switch (loArray.size()){
                        case 0:
                            p_oAPClient.updateObject("sTermCode", "");
                            p_oAPClient.updateObject("xTermName", "");
                            p_oAPClient.updateRow();
                            break;
                        default:
                            loJSON = (JSONObject) loArray.get(0);
                            p_oAPClient.updateObject("sTermCode", (String) loJSON.get("sTermCode"));
                            p_oAPClient.updateObject("xTermName", (String) loJSON.get("sDescript"));
                            p_oAPClient.updateRow();
                    }
                } catch (ParseException ex) {
                    ex.printStackTrace();
                    p_oAPClient.updateObject("sTermCode", "");
                    p_oAPClient.updateObject("xTermName", "");
                    p_oAPClient.updateRow();
                }

                p_oListener.MasterRetreive("sTermCode", (String) p_oAPClient.getObject("sTermCode"));
                p_oListener.MasterRetreive("xTermName", (String) p_oAPClient.getObject("xTermName"));
            } catch (SQLException ex) {
                ex.printStackTrace();
                setMessage("SQLException on " + lsProcName + ". Please inform your System Admin.");
            }
        }
    }
    
    public JSONObject searchClient(String fsKey, Object foValue, String fsFilter, String fsValue, boolean fbExact){
        p_oClient.setKey(fsKey);
        p_oClient.setValue(foValue);
        p_oClient.setExact(fbExact);
        
        return p_oClient.Search();
    }
    
    public ClientSearch getSearchClient(){
        return p_oClient;
    }
    
    private void getClient(String foValue){
        String lsProcName = this.getClass().getSimpleName() + ".getClient()";
        
        JSONObject loJSON = searchTerm("sClientID", foValue, "", "", true);
        if ("success".equals((String) loJSON.get("result"))){
            try {
                JSONParser loParser = new JSONParser();

                p_oAPClient.first();
                try {
                    JSONArray loArray = (JSONArray) loParser.parse((String) loJSON.get("payload"));

                    switch (loArray.size()){
                        case 0:
                            p_oAPClient.updateObject("sClientID", "");
                            p_oAPClient.updateObject("xClientNm", "");
                            p_oAPClient.updateObject("xLastName", "");
                            p_oAPClient.updateObject("xFrstName", "");
                            p_oAPClient.updateObject("xMiddName", "");
                            p_oAPClient.updateObject("xAddressx", "");
                            p_oAPClient.updateRow();
                            break;
                        default:
                            loJSON = (JSONObject) loArray.get(0);
                            p_oAPClient.updateObject("sClientID", (String) loJSON.get("sClientID"));
                            p_oAPClient.updateObject("xClientNm", (String) loJSON.get("sClientNm"));
                            p_oAPClient.updateObject("xLastName", (String) loJSON.get("sLastName"));
                            p_oAPClient.updateObject("xFrstName", (String) loJSON.get("sFrstName"));
                            p_oAPClient.updateObject("xMiddName", (String) loJSON.get("sMiddName"));
                            p_oAPClient.updateObject("xAddressx", "");
                            p_oAPClient.updateRow();
                    }
                } catch (ParseException ex) {
                    ex.printStackTrace();
                    p_oAPClient.updateObject("sClientID", "");
                    p_oAPClient.updateObject("xClientNm", "");
                    p_oAPClient.updateObject("xLastName", "");
                    p_oAPClient.updateObject("xFrstName", "");
                    p_oAPClient.updateObject("xMiddName", "");
                    p_oAPClient.updateObject("xAddressx", "");
                    p_oAPClient.updateRow();
                }

                p_oListener.MasterRetreive("sClientID", (String) p_oAPClient.getObject("sClientID"));
                p_oListener.MasterRetreive("xClientNm", (String) p_oAPClient.getObject("xClientNm"));
                p_oListener.MasterRetreive("xLastName", (String) p_oAPClient.getObject("xLastName"));
                p_oListener.MasterRetreive("xFrstName", (String) p_oAPClient.getObject("xFrstName"));
                p_oListener.MasterRetreive("xMiddName", (String) p_oAPClient.getObject("xMiddName"));
                p_oListener.MasterRetreive("xAddressx", (String) p_oAPClient.getObject("xAddressx"));
            } catch (SQLException ex) {
                ex.printStackTrace();
                setMessage("SQLException on " + lsProcName + ". Please inform your System Admin.");
            }
        }
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
    
    private String getSQ_Master(){
        return "SELECT" +
                    ", a.sClientID" +
                    ", a.sBranchCd" +
                    ", a.sCPerson1" +
                    ", a.sCPPosit1" +
                    ", a.sTelNoxxx" +
                    ", a.sFaxNoxxx" +
                    ", a.sRemarksx" +
                    ", a.sTermCode" +
                    ", a.nDiscount" +
                    ", a.nCredLimt" +
                    ", a.dBalForwd" +
                    ", a.nBalForwd" +
                    ", a.nOBalance" +
                    ", a.nABalance" +
                    ", a.dCltSince" +
                    ", a.cRecdStat" +
                    ", a.dModified" +
                    ", c.sClientNm xCompnyNm" +
                    ", c.sLastName xLastName" +
                    ", c.sFrstName xFirstNme" +
                    ", c.sMiddName xMiddName" +
                    ", '' xAddressx" +
                    ", IFNULL(b.sDescript, '') xTermName" +
                " FROM " + MASTER_TABLE + " a" +
                        " LEFT JOIN Term b ON a.sTermCode = b.sTermCode" +
                    ", Client_Master c" +
                " WHERE a.sClientID = c.sClientID";
    }
    
    private void initMaster() throws SQLException{
        p_oAPClient.last();
        p_oAPClient.moveToInsertRow();
        
        MiscUtil.initRowSet(p_oAPClient);
        p_oAPClient.updateObject("cRecdStat", RecordStatus.ACTIVE);
        
        p_oAPClient.insertRow();
        p_oAPClient.moveToCurrentRow();
    }
    
    private boolean isEntryOK(){
        try {
            //assign values to master record
            p_oAPClient.first();
            
            if (String.valueOf(getMaster("sClientID")).isEmpty()){
                setMessage("Supplier is must not be empty.");
                return false;
            }
            
            p_oAPClient.updateObject("sBranchCd", (String) p_oNautilus.getBranchConfig("sBranchCd"));
            p_oAPClient.updateObject("dModified", p_oNautilus.getServerDate());
            p_oAPClient.updateRow();

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            setMessage(e.getMessage());
            return false;
        }
    }
}
