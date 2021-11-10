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

public class ARClient implements XRecord{    
    private final String MASTER_TABLE = "AR_Master";
    
    private final XNautilus p_oNautilus;
    private final boolean p_bWithParent;
    private final String p_sBranchCd;
    
    private final ParamSearchF p_oTerm;
    private final ClientSearch p_oClient;
    
    private LRecordMas p_oListener;    
    private String p_sMessagex;
    private int p_nEditMode;
    
    private CachedRowSet p_oARClient;
    
    public ARClient(XNautilus foNautilus, String fsBranchCd, boolean fbWithParent){
        p_oNautilus = foNautilus;
        p_sBranchCd = fsBranchCd;
        p_bWithParent = fbWithParent;
        
        p_oTerm = new ParamSearchF(p_oNautilus, ParamSearchF.SearchType.searchTerm);
        p_oClient = new ClientSearch(p_oNautilus, ClientSearch.SearchType.searchCustomer);
        
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
            p_oARClient = factory.createCachedRowSet();
            p_oARClient.populate(loRS);
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
                
                lsSQL = MiscUtil.rowset2SQL(p_oARClient, MASTER_TABLE, "sClientNm;sLastName;sFrstName;sMiddName;sSuffixNm;xAddressx;xTermName");
            } else { //old record
                lsSQL = MiscUtil.rowset2SQL(p_oARClient, MASTER_TABLE, "sClientNm;sLastName;sFrstName;sMiddName;sSuffixNm;xAddressx;xTermName", "sClientID = " + SQLUtil.toSQL((String) getMaster("sClientID")));
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
                
                if (!p_bWithParent) p_oNautilus.rollbackTrans();
                
                p_nEditMode = EditMode.UNKNOWN;
                return false;
            } 

            if (!p_bWithParent) p_oNautilus.commitTrans();
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
            if (p_oARClient != null){
                p_oARClient.first();

                if (p_oARClient.getString("sClientID").equals(fsClientID)){
                    p_nEditMode  = EditMode.READY;
                    return true;
                }
            }
            
            String lsSQL;
            ResultSet loRS;
            
            RowSetFactory factory = RowSetProvider.newFactory();
            
            //open master record
            lsSQL = MiscUtil.addCondition(getSQ_Master(), 
                        " a.sClientID = " + SQLUtil.toSQL(fsClientID) +
                            " AND a.sBranchCd = " + SQLUtil.toSQL(p_sBranchCd));
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oARClient = factory.createCachedRowSet();
            p_oARClient.populate(loRS);
            MiscUtil.close(loRS);
            
            if (p_oARClient.size() == 1) {                            
                p_nEditMode  = EditMode.READY;
                return true;
            }
            
            if (NewRecord()){
                ClientMaster loClient = new ClientMaster(p_oNautilus, p_sBranchCd, true);
                
                if (loClient.OpenRecord(fsClientID)){
                    p_oARClient.first();
                    p_oARClient.updateObject(1, (String) loClient.getMaster("sClientID"));
                    p_oARClient.updateObject(21, (String) loClient.getMaster("sClientNm"));
                    p_oARClient.updateObject(22, (String) loClient.getMaster("sLastName"));
                    p_oARClient.updateObject(23, (String) loClient.getMaster("sFrstName"));
                    p_oARClient.updateObject(24, (String) loClient.getMaster("sMiddName"));
                    p_oARClient.updateObject(25, (String) loClient.getMaster("sSuffixNm"));
                    p_oARClient.updateObject(26, (String) loClient.getMaster("xAddressx"));
                    p_oARClient.updateRow();
                    return true;
                }
            }
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
    
    @Override
    public Object getMaster(int fnIndex) {
        try {
            p_oARClient.first();
            return p_oARClient.getObject(fnIndex);
        } catch (SQLException e) {
            return null;
        }
    }

    @Override
    public void setMaster(int fnIndex, Object foValue) {
        String lsProcName = this.getClass().getSimpleName() + ".setMaster(int fnIndex, Object foValue)";
        
        if (p_nEditMode != EditMode.ADDNEW &&
            p_nEditMode != EditMode.UPDATE){
            System.err.println("Transaction is not on update mode.");
            return;
        }
        
        try {
            switch (fnIndex){
                case 10: //nDiscount
                case 11: //nCredLimt
                case 13: //nBalForwd
                case 14: //nOBalance
                case 15: //nABalance
                    p_oARClient.first();
                    
                    if (StringUtil.isNumeric(String.valueOf(foValue))){
                        p_oARClient.updateObject(fnIndex, foValue);
                        p_oARClient.updateRow();
                    }
                    
                    p_oListener.MasterRetreive(fnIndex, p_oARClient.getObject(fnIndex));
                    break;
                case 12: //dBalForwd
                case 16: //dCltSince
                    p_oARClient.first();
                    
                    if (StringUtil.isDate((String) foValue, SQLUtil.FORMAT_SHORT_DATE)){
                        p_oARClient.updateObject(fnIndex, foValue);
                        p_oARClient.updateRow();
                    }
                    
                    p_oListener.MasterRetreive(fnIndex, p_oARClient.getObject(fnIndex));
                    break;
                case 1: //sClientID
                    getClient((String) foValue);
                    break;
                case 9: //sTermCode
                    getTerm((String) foValue);
                    break;
                default:
                    p_oARClient.first();
                    p_oARClient.updateObject(fnIndex, foValue);
                    p_oARClient.updateRow();

                    p_oListener.MasterRetreive(fnIndex, p_oARClient.getObject(fnIndex));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            setMessage("SQLException on " + lsProcName + ". Please inform your System Admin.");
        }
    }

    @Override
    public void setMaster(String fsIndex, Object foValue){
        String lsProcName = this.getClass().getSimpleName() + ".setMaster(String fsIndex, Object foValue)";
        
        try {
            setMaster(MiscUtil.getColumnIndex(p_oARClient, fsIndex), foValue);
        } catch (SQLException e) {
            e.printStackTrace();
            setMessage("SQLException on " + lsProcName + ". Please inform your System Admin.");
        }
    }

    @Override
    public Object getMaster(String fsFieldNm){
        try {
            return getMaster(MiscUtil.getColumnIndex(p_oARClient, fsFieldNm));
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

                p_oARClient.first();
                try {
                    JSONArray loArray = (JSONArray) loParser.parse((String) loJSON.get("payload"));

                    switch (loArray.size()){
                        case 0:
                            p_oARClient.updateObject("sTermCode", "");
                            p_oARClient.updateObject("xTermName", "");
                            p_oARClient.updateRow();
                            break;
                        default:
                            loJSON = (JSONObject) loArray.get(0);
                            p_oARClient.updateObject("sTermCode", (String) loJSON.get("sTermCode"));
                            p_oARClient.updateObject("xTermName", (String) loJSON.get("sDescript"));
                            p_oARClient.updateRow();
                    }
                } catch (ParseException ex) {
                    ex.printStackTrace();
                    p_oARClient.updateObject("sTermCode", "");
                    p_oARClient.updateObject("xTermName", "");
                    p_oARClient.updateRow();
                }

                p_oListener.MasterRetreive("sTermCode", (String) p_oARClient.getObject("sTermCode"));
                p_oListener.MasterRetreive("xTermName", (String) p_oARClient.getObject("xTermName"));
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

                p_oARClient.first();
                try {
                    JSONArray loArray = (JSONArray) loParser.parse((String) loJSON.get("payload"));

                    switch (loArray.size()){
                        case 0:
                            p_oARClient.updateObject("sClientID", "");
                            p_oARClient.updateObject("xClientNm", "");
                            p_oARClient.updateObject("xLastName", "");
                            p_oARClient.updateObject("xFrstName", "");
                            p_oARClient.updateObject("xMiddName", "");
                            p_oARClient.updateObject("xAddressx", "");
                            p_oARClient.updateRow();
                            break;
                        default:
                            loJSON = (JSONObject) loArray.get(0);
                            p_oARClient.updateObject("sClientID", (String) loJSON.get("sClientID"));
                            p_oARClient.updateObject("xClientNm", (String) loJSON.get("sClientNm"));
                            p_oARClient.updateObject("xLastName", (String) loJSON.get("sLastName"));
                            p_oARClient.updateObject("xFrstName", (String) loJSON.get("sFrstName"));
                            p_oARClient.updateObject("xMiddName", (String) loJSON.get("sMiddName"));
                            p_oARClient.updateObject("xAddressx", "");
                            p_oARClient.updateRow();
                    }
                } catch (ParseException ex) {
                    ex.printStackTrace();
                    p_oARClient.updateObject("sClientID", "");
                    p_oARClient.updateObject("xClientNm", "");
                    p_oARClient.updateObject("xLastName", "");
                    p_oARClient.updateObject("xFrstName", "");
                    p_oARClient.updateObject("xMiddName", "");
                    p_oARClient.updateObject("xAddressx", "");
                    p_oARClient.updateRow();
                }

                p_oListener.MasterRetreive("sClientID", (String) p_oARClient.getObject("sClientID"));
                p_oListener.MasterRetreive("xClientNm", (String) p_oARClient.getObject("xClientNm"));
                p_oListener.MasterRetreive("xLastName", (String) p_oARClient.getObject("xLastName"));
                p_oListener.MasterRetreive("xFrstName", (String) p_oARClient.getObject("xFrstName"));
                p_oListener.MasterRetreive("xMiddName", (String) p_oARClient.getObject("xMiddName"));
                p_oListener.MasterRetreive("xAddressx", (String) p_oARClient.getObject("xAddressx"));
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
                    "  a.sClientID" +
                    ", a.sBranchCd" +
                    ", a.sNmeOnChk" +
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
                    ", a.cAutoHold" +
                    ", a.cHoldAcct" +
                    ", a.cRecdStat" +
                    ", a.dModified" +
                    ", c.sClientNm xCompnyNm" +
                    ", c.sLastName xLastName" +
                    ", c.sFrstName xFirstNme" +
                    ", c.sMiddName xMiddName" +
                    ", c.sSuffixNm xSuffixNm" +
                    ", TRIM(CONCAT(d.sHouseNox, ' ', sAddressx, ', ', IFNULL(e.sBrgyName, ''), ' ', IFNULL(f.sTownName, ''))) xAddressx" +
                    ", IFNULL(b.sDescript, '') xTermName" +
                " FROM " + MASTER_TABLE + " a" +
                    " LEFT JOIN Term b ON a.sTermCode = b.sTermCode" +
                    ", Client_Master c" +
                    " LEFT JOIN Client_Address d" +
                        " LEFT JOIN Barangay e ON d.sBrgyIDxx = e.sBrgyIDxx" +
                        " LEFT JOIN TownCity f ON d.sTownIDxx = f.sTownIDxx" +
                    " ON c.sClientID = d.sClientID" +
                        " AND d.nPriority = 1" +
                " WHERE a.sClientID = c.sClientID";
    }
    
    private void initMaster() throws SQLException{
        p_oARClient.last();
        p_oARClient.moveToInsertRow();
        
        MiscUtil.initRowSet(p_oARClient);
        p_oARClient.updateObject("cRecdStat", RecordStatus.ACTIVE);
        
        p_oARClient.insertRow();
        p_oARClient.moveToCurrentRow();
    }
    
    private boolean isEntryOK(){
        try {
            //assign values to master record
            p_oARClient.first();
            
            if (String.valueOf(getMaster("sClientID")).isEmpty()){
                setMessage("Supplier must not be empty.");
                return false;
            }
            
            p_oARClient.updateObject("sBranchCd", (String) p_oNautilus.getBranchConfig("sBranchCd"));
            p_oARClient.updateObject("dModified", p_oNautilus.getServerDate());
            p_oARClient.updateRow();

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            setMessage(e.getMessage());
            return false;
        }
    }
}
