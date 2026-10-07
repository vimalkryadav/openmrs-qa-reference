package org.openmrs.module.reporting.web.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.openmrs.module.reporting.common.SortCriteria;
import org.openmrs.module.reporting.dataset.column.definition.RowPerObjectColumnDefinition;
import org.openmrs.module.reporting.dataset.definition.*;
import org.openmrs.module.reporting.definition.DefinitionUtil;
import org.openmrs.module.reporting.evaluation.parameter.*;
import org.openmrs.module.reporting.report.definition.ReportDefinition;

/** Scoped transport for native object-dataset runtime columns and sort criteria. */
public final class StructuredReportParameters {
    private static final ObjectMapper JSON = new ObjectMapper();
    private StructuredReportParameters() { }

    public static final class Target {
        final RowPerObjectDataSetDefinition definition;
        final String field;
        Target(RowPerObjectDataSetDefinition definition, String field) { this.definition=definition; this.field=field; }
    }

    private static boolean supported(RowPerObjectDataSetDefinition definition) {
        return definition instanceof ObsDataSetDefinition || definition instanceof VisitDataSetDefinition
            || definition instanceof EncounterAndObsDataSetDefinition;
    }

    public static Map<String,List<Target>> targets(Parameterizable value) {
        Map<String,List<Target>> result=new LinkedHashMap<String,List<Target>>();
        if (value instanceof RowPerObjectDataSetDefinition) {
            add(result,(RowPerObjectDataSetDefinition)value,null,value);
        } else if (value instanceof ReportDefinition) {
            for (Mapped<? extends DataSetDefinition> mapped : ((ReportDefinition)value).getDataSetDefinitions().values()) {
                if (mapped.getParameterizable() instanceof RowPerObjectDataSetDefinition)
                    add(result,(RowPerObjectDataSetDefinition)mapped.getParameterizable(),mapped.getParameterMappings(),value);
            }
        }
        return result;
    }

    private static void add(Map<String,List<Target>> result, RowPerObjectDataSetDefinition definition,
                            Map<String,Object> mappings, Parameterizable owner) {
        if (!supported(definition)) return;
        for (String field : Arrays.asList("columnDefinitions","sortCriteria")) {
            if (definition.getParameter(field)==null) continue;
            String name=field;
            if (mappings!=null) {
                Object expression=mappings.get(field);
                if (!(expression instanceof String) || !((String)expression).matches("\\$\\{[^{}]+\\}")) continue;
                name=((String)expression).substring(2,((String)expression).length()-1);
            }
            Parameter parameter=owner.getParameter(name);
            if (parameter==null) continue;
            Class<?> expected="columnDefinitions".equals(field)?RowPerObjectColumnDefinition.class:SortCriteria.class;
            if (parameter.getType()!=expected || ("columnDefinitions".equals(field) && parameter.getCollectionType()==null))
                throw new IllegalArgumentException("Runtime parameter "+name+" must use the native "+field+" type");
            if (!result.containsKey(name)) result.put(name,new ArrayList<Target>());
            for (Target previous : result.get(name)) if (!previous.field.equals(field) || previous.definition.getClass()!=definition.getClass())
                throw new IllegalArgumentException("Runtime parameter "+name+" is shared by incompatible dataset fields");
            result.get(name).add(new Target(definition,field));
        }
    }

    public static Object parse(Parameterizable owner, String name, Object input) {
        List<Target> selected=targets(owner).get(name);
        if (selected==null) throw new IllegalArgumentException("Unsupported structured parameter "+name);
        Target target=selected.get(0);
        if (input instanceof SortCriteria) return input;
        if (input instanceof List && !((List<?>)input).isEmpty() && ((List<?>)input).get(0) instanceof RowPerObjectColumnDefinition) return input;
        try { return RowObjectDefinitionEditor.parse(target.definition,target.field,input instanceof String?(String)input:JSON.writeValueAsString(input==null?Collections.emptyList():input)); }
        catch (Exception invalid) { throw new IllegalArgumentException("Invalid runtime parameter "+name+": "+invalid.getMessage(),invalid); }
    }

    @SuppressWarnings("unchecked")
    public static void validate(Parameterizable owner, Map<String,Object> values) {
        Map<RowPerObjectDataSetDefinition,RowPerObjectDataSetDefinition> drafts=new IdentityHashMap<RowPerObjectDataSetDefinition,RowPerObjectDataSetDefinition>();
        for (Map.Entry<String,List<Target>> entry : targets(owner).entrySet()) {
            for (Target target : entry.getValue()) {
                RowPerObjectDataSetDefinition draft=drafts.get(target.definition);
                if (draft==null) { draft=(RowPerObjectDataSetDefinition)DefinitionUtil.clone(target.definition); drafts.put(target.definition,draft); }
                Object value=values.get(entry.getKey());
                if (value==null) value=owner.getParameter(entry.getKey()).getDefaultValue();
                if (value instanceof String) value=parse(owner,entry.getKey(),value);
                draft.removeParameter(target.field);
                if ("columnDefinitions".equals(target.field)) draft.setColumnDefinitions(value==null?Collections.<RowPerObjectColumnDefinition>emptyList():(List<RowPerObjectColumnDefinition>)value);
                else draft.setSortCriteria((SortCriteria)value);
            }
        }
        for (RowPerObjectDataSetDefinition draft : drafts.values()) RowObjectDefinitionEditor.validate(draft);
    }

    @SuppressWarnings("unchecked")
    public static Object transport(Parameterizable owner, String name, Object value) {
        List<Target> selected=targets(owner).get(name);
        if (selected==null || value==null || value instanceof String) return value;
        Target target=selected.get(0);
        try {
            RowPerObjectDataSetDefinition draft=target.definition.getClass().newInstance();
            if ("columnDefinitions".equals(target.field)) draft.setColumnDefinitions((List<RowPerObjectColumnDefinition>)value);
            else draft.setSortCriteria((SortCriteria)value);
            return JSON.readValue(RowObjectDefinitionEditor.model(draft),Map.class).get("columnDefinitions".equals(target.field)?"columns":"sorts");
        } catch (Exception invalid) { throw new IllegalArgumentException("Cannot represent runtime parameter "+name,invalid); }
    }

    /** Groups share one set of column choices when columns and sorts are both runtime inputs. */
    @SuppressWarnings("unchecked")
    public static Map<String,Object> model(Parameterizable owner, Map<String,Object> values) throws Exception {
        Map<String,List<Target>> targets=targets(owner);
        Map<String,Object> fields=new LinkedHashMap<String,Object>();
        Map<RowPerObjectDataSetDefinition,Map<String,Object>> groups=new LinkedHashMap<RowPerObjectDataSetDefinition,Map<String,Object>>();
        int serial=0;
        for (Map.Entry<String,List<Target>> entry : targets.entrySet()) {
            Target target=entry.getValue().get(0);
            Map<String,Object> group=groups.get(target.definition);
            if (group==null) {
                group=JSON.readValue(RowObjectDefinitionEditor.model(target.definition),Map.class);
                group.put("fields",new LinkedHashMap<String,Object>()); group.put("prefix","runtime-object-"+(++serial));
                groups.put(target.definition,group);
            }
            Map<String,Object> field=new LinkedHashMap<String,Object>();
            field.put("id",group.get("prefix")+"-"+target.field); field.put("field",target.field);
            fields.put(entry.getKey(),field);
            ((Map<String,Object>)group.get("fields")).put(target.field,field.get("id"));
            Object input=values==null?null:values.get(entry.getKey());
            if (input==null) input=owner.getParameter(entry.getKey()).getDefaultValue();
            Object rows=null;
            if (input instanceof String) {
                try { rows=JSON.readValue((String)input,List.class); }
                catch (Exception invalid) { field.put("invalidInput",input); }
            } else if (input!=null) {
                RowPerObjectDataSetDefinition draft=target.definition.getClass().newInstance();
                if ("columnDefinitions".equals(target.field)) draft.setColumnDefinitions((List<RowPerObjectColumnDefinition>)input);
                else draft.setSortCriteria((SortCriteria)input);
                rows=JSON.readValue(RowObjectDefinitionEditor.model(draft),Map.class).get("columnDefinitions".equals(target.field)?"columns":"sorts");
            }
            if (rows!=null) group.put("columnDefinitions".equals(target.field)?"columns":"sorts",rows);
        }
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        result.put("structuredFields",fields); result.put("structuredGroupsJson",JSON.writeValueAsString(groups.values()));
        return result;
    }
}
