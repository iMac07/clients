package org.xersys.clients.base;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;
import javax.sql.rowset.RowSetProvider;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.xersys.commander.contants.EditMode;
import org.xersys.commander.contants.RecordStatus;
import org.xersys.commander.iface.LMasDetTrans;
import org.xersys.commander.iface.XNautilus;
import org.xersys.commander.util.MiscUtil;
import org.xersys.commander.util.SQLUtil;
import org.xersys.parameters.search.ParamSearchF;

public class ClientAddress {
    private final XNautilus p_oNautilus;
    private final ParamSearchF p_oBarangay;
    private final ParamSearchF p_oTownCity;
    
    private LMasDetTrans _listener;
    
    private String p_sMessagex;
    private boolean p_bLoaded;
    private int p_nEditMode;
    
    private CachedRowSet p_oAddress;
    
    public ClientAddress(XNautilus foNautilus){
        p_oNautilus = foNautilus;
        
        p_oBarangay = new ParamSearchF(p_oNautilus, ParamSearchF.SearchType.searchBarangay);
        p_oTownCity = new ParamSearchF(p_oNautilus, ParamSearchF.SearchType.searchTownCity);
    }
    
    public void setListener(Object foListener) {
        _listener = (LMasDetTrans) foListener;
    }
    
    public CachedRowSet getData(){
        return p_oAddress;
    }
    
    public boolean LoadRecord(CachedRowSet foValue) throws SQLException{
        System.out.println(this.getClass().getSimpleName() + ".LoadRecord(CachedRowSet foValue)");
        
        if (p_oNautilus == null){
            p_sMessagex = "Application driver is not set.";
            return false;
        }
        
        p_oAddress = foValue;
        
        AddItem();
        
        p_nEditMode = EditMode.UPDATE;
        
        p_bLoaded = true;
        return true;
    }
    
    public boolean LoadRecord(String fsClientID) throws SQLException{
        System.out.println(this.getClass().getSimpleName() + ".LoadRecord(String fsClientID)");
        
        if (p_oNautilus == null){
            p_sMessagex = "Application driver is not set.";
            return false;
        }
        
        String lsSQL;
        ResultSet loRS;
        
        RowSetFactory factory = RowSetProvider.newFactory();
        
        if (fsClientID.isEmpty()){
            lsSQL = MiscUtil.addCondition(getSQ_Master(), "0=1");
            loRS = p_oNautilus.executeQuery(lsSQL);
            p_oAddress = factory.createCachedRowSet();
            p_oAddress.populate(loRS);
            MiscUtil.close(loRS);
            
            AddItem();
            
            p_nEditMode = EditMode.ADDNEW;
        } else {
            loRS = p_oNautilus.executeQuery(MiscUtil.addCondition(getSQ_Master(), "sClientID = " + SQLUtil.toSQL(fsClientID)));
            
            p_oAddress = factory.createCachedRowSet();
            p_oAddress.populate(loRS);
            MiscUtil.close(loRS);
            
            AddItem();
            
            p_nEditMode = EditMode.UPDATE;
        }
        
        p_bLoaded = true;
        return true;
    }
    
    public int getItemCount(){
        try {
            p_oAddress.last();
            return p_oAddress.getRow();
        } catch (SQLException e) {
            e.printStackTrace();
            setMessage(e.getMessage());
            return -1;
        }
    }
    
    public boolean AddItem() throws SQLException{
        if (p_oAddress.size()== 0){
            p_oAddress.moveToInsertRow();
        
            MiscUtil.initRowSet(p_oAddress);
            p_oAddress.updateObject("nEntryNox", p_oAddress.size() + 1);
            p_oAddress.updateObject("nPriority", p_oAddress.size() + 1);
            p_oAddress.updateObject("cRecdStat", "1");

            p_oAddress.insertRow();
            p_oAddress.moveToCurrentRow();
        } else {
            p_oAddress.last();
        
            if (!((String) p_oAddress.getObject("sTownIDxx")).isEmpty()){
                p_oAddress.moveToInsertRow();

                MiscUtil.initRowSet(p_oAddress);
                p_oAddress.updateObject("nEntryNox", p_oAddress.size() + 1);
                p_oAddress.updateObject("nPriority", p_oAddress.size() + 1);
                p_oAddress.updateObject("cRecdStat", "1");

                p_oAddress.insertRow();
                p_oAddress.moveToCurrentRow();
            } else {
                setMessage("Town must not be empty.");
                return false;
            }
        }

        return true;
    }
    
    public boolean RemoveItem(int fnRow) throws SQLException{
        if (!p_bLoaded){
            setMessage("No record was loaded.");
            return false;
        }
        
        if (fnRow < 0){
            setMessage("Invalid row item.");
            return false;
        }
        
        p_oAddress.absolute(fnRow + 1);
        if (!((String) p_oAddress.getObject("sClientID")).isEmpty()){
            p_oAddress.updateObject("cRecdStat", RecordStatus.INACTIVE);
            p_oAddress.updateRow();
        } else {
            p_oAddress.deleteRow();
            AddItem();
        }
            
        return true;
    }
    
    public void setDetail(int fnRow, String fsFieldNm, Object foValue) throws SQLException, ParseException {
        if (p_nEditMode != EditMode.ADDNEW &&
            p_nEditMode != EditMode.UPDATE){
            System.err.println("Transaction is not on update mode.");
            return;
        }
        
        switch (fsFieldNm){
            case "nPriority":
                if (!((String) p_oAddress.getObject("sAddressx")).isEmpty()){
                    setPriority(fnRow, (int) foValue);

                    _listener.DetailRetreive(fnRow, fsFieldNm, "");
                }
                break;
            case "sBrgyIDxx":
            case "sTownIDxx":
                p_oAddress.absolute(fnRow + 1);
                getDetail(fsFieldNm, (String) foValue);
                _listener.DetailRetreive(fnRow, fsFieldNm, "");
                break;
            default:
                p_oAddress.absolute(fnRow + 1);
                p_oAddress.updateObject(fsFieldNm, foValue);
                p_oAddress.updateRow();

                _listener.DetailRetreive(fnRow, fsFieldNm, "");
        }
    }

    public Object getDetail(int fnRow, String fsFieldNm) throws SQLException{
        p_oAddress.absolute(fnRow + 1);
        return p_oAddress.getObject(fsFieldNm);
    }
    
    public JSONObject searchBarangay(String fsKey, Object foValue, boolean fbExact){
        p_oBarangay.setKey(fsKey);
        p_oBarangay.setValue(foValue);
        p_oBarangay.setExact(fbExact);
        
        return p_oBarangay.Search();
    }
    
    public ParamSearchF getSearchBarangay(){
        return p_oBarangay;
    }
    
    public JSONObject searchTown(String fsKey, Object foValue, boolean fbExact){
        p_oTownCity.setKey(fsKey);
        p_oTownCity.setValue(foValue);
        p_oTownCity.setExact(fbExact);
        
        return p_oTownCity.Search();
    }
    
    public ParamSearchF getSearchTown(){
        return p_oTownCity;
    }
    
    private void getDetail(String fsFieldNm, Object foValue) throws SQLException, ParseException{       
        JSONObject loJSON;
        JSONParser loParser = new JSONParser();
        
        switch(fsFieldNm){
            case "sBrgyIDxx":
                loJSON = searchBarangay("a.sBrgyIDxx", foValue, true);
                
                if ("success".equals((String) loJSON.get("result"))){
                    loJSON = (JSONObject) ((JSONArray) loParser.parse((String) loJSON.get("payload"))).get(0);
                    
                    p_oAddress.updateObject("sBrgyIDxx", (String) loJSON.get("sBrgyIDxx"));
                    p_oAddress.updateObject("xBrgyName", (String) loJSON.get("sBrgyName"));
                    p_oAddress.updateObject("sTownIDxx", (String) loJSON.get("sTownIDxx"));
                    p_oAddress.updateObject("xTownName", (String) loJSON.get("xTownName"));
                    p_oAddress.updateObject("xProvName", (String) loJSON.get("xProvName"));
                    p_oAddress.updateRow();
                    
                }
                break;
            case "sTownIDxx":
                loJSON = searchBarangay("a.sTownIDxx", foValue, true);
                
                if ("success".equals((String) loJSON.get("result"))){
                    loJSON = (JSONObject) ((JSONArray) loParser.parse((String) loJSON.get("payload"))).get(0);
                    
                    //the user selects different town from baragay town
                    if (!p_oAddress.getString("sTownIDxx").equals((String) loJSON.get("sTownIDxx"))){
                        p_oAddress.updateObject("sBrgyIDxx", "");
                        p_oAddress.updateObject("xBrgyName", "");
                    }
                    p_oAddress.updateObject("sTownIDxx", (String) loJSON.get("sTownIDxx"));
                    p_oAddress.updateObject("xTownName", (String) loJSON.get("xTownName"));
                    p_oAddress.updateObject("xProvName", (String) loJSON.get("xProvName"));
                    p_oAddress.updateRow();
                }
                break;
        }
    }
    
    public String getMessage(){
        return p_sMessagex;
    }
    
    private void setMessage(String fsValue){
        p_sMessagex = fsValue;
    }
    
    private void setPriority(int fnRow, int fnAdd) throws SQLException, ParseException{
        p_oAddress.absolute(fnRow + 1);
        int lnOld = Integer.parseInt(String.valueOf(p_oAddress.getObject("nPriority")));
        int lnNew = lnOld - fnAdd;
        
        int lnCtr = 0;
        
        p_oAddress.beforeFirst();
        while (p_oAddress.next()){
            lnCtr++;
            if (fnRow + 1 >= lnCtr){
                if (fnRow + 1 == lnCtr){
                    p_oAddress.updateObject("nPriority", lnNew);
                    p_oAddress.updateRow();
                } else {
                    if (lnNew <= Integer.parseInt(String.valueOf(p_oAddress.getObject("nPriority")))){
                        p_oAddress.updateObject("nPriority", Integer.parseInt(String.valueOf(p_oAddress.getObject("nPriority"))) + fnAdd);
                        p_oAddress.updateRow();
                    }
                }
            } else break;
        }
        
        //sort the items based on priority
        JSONParser loParser = new JSONParser();
        
        String lsValue = MiscUtil.RS2JSON(p_oAddress).toJSONString();
        JSONArray laValue = (JSONArray) loParser.parse(lsValue);
        
        List<JSONObject> jsonValues = new ArrayList<JSONObject>();
        
        for (int i = 0; i < laValue.size(); i++) {
            jsonValues.add((JSONObject) laValue.get(i));
        }
        
        Collections.sort(jsonValues, new Comparator<JSONObject>() {
            private static final String KEY_NAME = "nPriority";
            
            String string1;
            String string2;
            
            @Override
            public int compare(JSONObject lhs, JSONObject rhs) {
                return (long) lhs.get(KEY_NAME) > (long) rhs.get(KEY_NAME) ? 1 : 
                    ((long) lhs.get(KEY_NAME) < (long) rhs.get(KEY_NAME) ? -1 : 0);
            }
        });
        
        String lsSQL = MiscUtil.addCondition(getSQ_Master(), "0=1");
        ResultSet loRS = p_oNautilus.executeQuery(lsSQL);
        p_oAddress.populate(loRS);
        MiscUtil.close(loRS);
        
        JSONObject loJSON;
        for (int i = 0; i <= jsonValues.size()-1; i++) {
            AddItem();
            
            loJSON = (JSONObject) jsonValues.get(i);
            
            p_oAddress.absolute(i + 1);
            p_oAddress.updateObject("sClientID", (String) loJSON.get("sClientID"));
            p_oAddress.updateObject("nEntryNox", (int) (long) loJSON.get("nEntryNox"));
            p_oAddress.updateObject("sHouseNox", (String) loJSON.get("sHouseNox"));
            p_oAddress.updateObject("sAddressx", (String) loJSON.get("sAddressx"));
            p_oAddress.updateObject("sBrgyIDxx", (String) loJSON.get("sBrgyIDxx"));
            p_oAddress.updateObject("sTownIDxx", (String) loJSON.get("sTownIDxx"));
            p_oAddress.updateObject("nPriority", (int) (long) loJSON.get("nPriority"));
            p_oAddress.updateObject("nLatitude", (double) loJSON.get("nLatitude"));
            p_oAddress.updateObject("nLongitud", (double) loJSON.get("nLongitud"));
            p_oAddress.updateObject("cPrimaryx", (String) loJSON.get("cPrimaryx"));
            p_oAddress.updateObject("cRecdStat", (String) loJSON.get("cRecdStat"));
            p_oAddress.updateObject("xBrgyName", (String) loJSON.get("xBrgyName"));
            p_oAddress.updateObject("xTownName", (String) loJSON.get("xTownName"));
            p_oAddress.updateObject("xProvName", (String) loJSON.get("xProvName"));
            p_oAddress.updateRow();
        }
    }
    
    private String getSQ_Master(){
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
}
