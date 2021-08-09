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

public class ClientEMail {
    private XNautilus p_oNautilus;
    private LMasDetTrans _listener;
    
    private String p_sMessagex;
    private boolean p_bLoaded;
    private int p_nEditMode;
    
    private CachedRowSet p_oMail;
    
    public ClientEMail(XNautilus foNautilus){
        p_oNautilus = foNautilus;
    }
    
    public void setListener(Object foListener) {
        _listener = (LMasDetTrans) foListener;
    }
    
    public CachedRowSet getData(){
        return p_oMail;
    }
    
    public boolean LoadRecord(CachedRowSet foValue) throws SQLException{
        System.out.println(this.getClass().getSimpleName() + ".LoadRecord(CachedRowSet foValue)");
        
        if (p_oNautilus == null){
            p_sMessagex = "Application driver is not set.";
            return false;
        }
        
        p_oMail = foValue;
        
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
            p_oMail = factory.createCachedRowSet();
            p_oMail.populate(loRS);
            MiscUtil.close(loRS);
            
            AddItem();
            
            p_nEditMode = EditMode.ADDNEW;
        } else {
            loRS = p_oNautilus.executeQuery(MiscUtil.addCondition(getSQ_Master(), "sClientID = " + SQLUtil.toSQL(fsClientID)));
            
            p_oMail = factory.createCachedRowSet();
            p_oMail.populate(loRS);
            MiscUtil.close(loRS);
            
            AddItem();
            
            p_nEditMode = EditMode.UPDATE;
        }
        
        p_bLoaded = true;
        return true;
    }
    
    public int getItemCount(){
        try {
            p_oMail.last();
            return p_oMail.getRow();
        } catch (SQLException e) {
            e.printStackTrace();
            setMessage(e.getMessage());
            return -1;
        }
    }
    
    public boolean AddItem() throws SQLException{
        if (p_oMail.size()== 0){
            p_oMail.moveToInsertRow();
        
            MiscUtil.initRowSet(p_oMail);
            p_oMail.updateObject("nEntryNox", p_oMail.size() + 1);
            p_oMail.updateObject("nPriority", p_oMail.size() + 1);
            p_oMail.updateObject("cRecdStat", "1");

            p_oMail.insertRow();
            p_oMail.moveToCurrentRow();
        } else {
            p_oMail.last();
        
            if (!((String) p_oMail.getObject("sEmailAdd")).isEmpty()){
                p_oMail.moveToInsertRow();

                MiscUtil.initRowSet(p_oMail);
                p_oMail.updateObject("nEntryNox", p_oMail.size() + 1);
                p_oMail.updateObject("nPriority", p_oMail.size() + 1);
                p_oMail.updateObject("cRecdStat", "1");

                p_oMail.insertRow();
                p_oMail.moveToCurrentRow();
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
        
        p_oMail.absolute(fnRow + 1);
        if (!((String) p_oMail.getObject("sClientID")).isEmpty()){
            p_oMail.updateObject("cRecdStat", RecordStatus.INACTIVE);
            p_oMail.updateRow();
        } else {
            p_oMail.deleteRow();
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
        
        if (fsFieldNm.equals("nPriority")) {
            if (!((String) p_oMail.getObject("sEmailAdd")).isEmpty()){
                setPriority(fnRow, (int) foValue);
                
                _listener.DetailRetreive(fnRow, fsFieldNm, "");
            }
        } else {
            p_oMail.absolute(fnRow + 1);
            p_oMail.updateObject(fsFieldNm, foValue);
            p_oMail.updateRow();
            
            AddItem();    
            
            _listener.DetailRetreive(fnRow, fsFieldNm, "");
        }
    }

    public Object getDetail(int fnRow, String fsFieldNm) throws SQLException{
        p_oMail.absolute(fnRow + 1);
        return p_oMail.getObject(fsFieldNm);
    }
    
    public String getMessage(){
        return p_sMessagex;
    }
    
    private void setMessage(String fsValue){
        p_sMessagex = fsValue;
    }
    
    private void setPriority(int fnRow, int fnAdd) throws SQLException, ParseException{
        p_oMail.absolute(fnRow + 1);
        int lnOld = (int) p_oMail.getObject("nPriority");
        int lnNew = lnOld - fnAdd;
        
        int lnCtr = 0;
        
        p_oMail.beforeFirst();
        while (p_oMail.next()){
            lnCtr++;
            if (fnRow + 1 >= lnCtr){
                if (fnRow + 1 == lnCtr){
                    p_oMail.updateObject("nPriority", lnNew);
                    p_oMail.updateRow();
                } else {
                    if (lnNew <= (int) p_oMail.getObject("nPriority")){
                        p_oMail.updateObject("nPriority", (int) p_oMail.getObject("nPriority") + fnAdd);
                        p_oMail.updateRow();
                    }
                }
            } else break;
        }
        
        //sort the items based on priority
        JSONParser loParser = new JSONParser();
        
        String lsValue = MiscUtil.RS2JSON(p_oMail).toJSONString();
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
        p_oMail.populate(loRS);
        MiscUtil.close(loRS);
        
        JSONObject loJSON;
        for (int i = 0; i <= jsonValues.size()-1; i++) {
            AddItem();
            
            loJSON = (JSONObject) jsonValues.get(i);
            
            p_oMail.absolute(i + 1);
            p_oMail.updateObject("sClientID", (String) loJSON.get("sClientID"));
            p_oMail.updateObject("nEntryNox", (int) (long) loJSON.get("nEntryNox"));
            p_oMail.updateObject("sEmailAdd", (String) loJSON.get("sEmailAdd"));
            p_oMail.updateObject("nPriority", (int) (long) loJSON.get("nPriority"));
            p_oMail.updateObject("cRecdStat", (String) loJSON.get("cRecdStat"));
            p_oMail.updateRow();
        }
    }
    
    private String getSQ_Master(){
        return "SELECT" +
                    "  sClientID" +
                    ", nEntryNox" +
                    ", sEmailAdd" +
                    ", nPriority" +
                    ", cRecdStat" +
                " FROM Client_eMail_Address" +
                " ORDER BY nPriority";
    }
}
