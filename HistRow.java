package com.assistente;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Model de linha exibida na tabela de Histórico (TableView).
 */
public class HistRow {
    private final StringProperty ts = new SimpleStringProperty("");
    private final StringProperty action = new SimpleStringProperty("");
    private final StringProperty target = new SimpleStringProperty("");
    private final StringProperty result = new SimpleStringProperty("");

    public HistRow(String ts, String action, String target, String result){
        this.ts.set(ts);
        this.action.set(action);
        this.target.set(target);
        this.result.set(result);
    }

    public StringProperty tsProperty(){ return ts; }
    public StringProperty actionProperty(){ return action; }
    public StringProperty targetProperty(){ return target; }
    public StringProperty resultProperty(){ return result; }
}
