package org.openmrs.module.reporting.web.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;
import org.openmrs.module.reporting.data.ConvertedDataDefinition;
import org.openmrs.module.reporting.data.DataDefinition;
import org.openmrs.module.reporting.data.converter.DataConverter;
import org.openmrs.module.reporting.definition.DefinitionContext;
import org.openmrs.module.reporting.evaluation.Definition;
import org.openmrs.module.reporting.evaluation.parameter.Mapped;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.htmlwidgets.web.handler.WidgetHandler;
import org.openmrs.util.HandlerUtil;

/** Form controls for the converted-definition fields absent from legacy htmlwidgets. */
public final class ConvertedDefinitionEditor {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PREFIX = "org.openmrs.module.reporting.data.converter.";
    private static final List<String> NAMES = Arrays.asList("AgeConverter", "AgeRangeConverter", "AttributeValueConverter", "BirthdateConverter", "BirthdateToAgeConverter", "BooleanConverter", "ChainedConverter", "ChangeInValueConverter", "CollectionConverter", "CollectionElementConverter", "ConcatenatedPropertyConverter", "ConceptCodeFromConceptConverter", "CountConverter", "DataSetRowConverter", "DateConverter", "EarliestCreatedConverter", "ExistenceConverter", "ListConverter", "MapConverter", "MapElementConverter", "MostRecentlyCreatedConverter", "NullValueConverter", "ObjectFormatter", "ObsFromObsGroupConverter", "ObsValueConverter", "ObsValueTextAsCodedConverter", "PropertyConverter", "StringConverter");
    private ConvertedDefinitionEditor() { }

    @SuppressWarnings("unchecked")
    private static Class<? extends Definition> sourceType(Definition definition) throws Exception {
        for (String kind : Arrays.asList("person", "patient", "encounter", "obs")) {
            String label = Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
            Class<?> candidate = Class.forName("org.openmrs.module.reporting.data."+kind+".definition."+label+"DataDefinition");
            if (candidate.isInstance(definition)) return (Class<? extends Definition>) candidate;
        }
        throw new IllegalArgumentException("Unsupported converted definition type");
    }

    public static String model(Definition definition) throws Exception {
        ConvertedDataDefinition<?> converted = (ConvertedDataDefinition<?>) definition;
        Map<String,Object> model = new LinkedHashMap<String,Object>();
        List<Object> definitions = new ArrayList<Object>();
        for (Definition source : DefinitionContext.getAllDefinitions(sourceType(definition), false)) {
            if (source.getUuid().equals(definition.getUuid())) continue;
            Map<String,Object> row = new LinkedHashMap<String,Object>();
            row.put("uuid", source.getUuid()); row.put("name", source.getName());
            List<Object> parameters = new ArrayList<Object>();
            for (Parameter parameter : source.getParameters()) {
                Map<String,Object> p = new LinkedHashMap<String,Object>();
                p.put("name", parameter.getName()); p.put("label", parameter.getLabel());
                parameters.add(p);
            }
            row.put("parameters",parameters); definitions.add(row);
        }
        model.put("definitions",definitions);
        Map<String,Object> mapped = new LinkedHashMap<String,Object>();
        if (converted.getDefinitionToConvert()!=null) {
            mapped.put("uuid",converted.getDefinitionToConvert().getParameterizable().getUuid());
            mapped.put("mappings",converted.getDefinitionToConvert().getParameterMappings());
        }
        model.put("mapped",mapped);
        List<Object> current = new ArrayList<Object>();
        for (DataConverter converter : converted.getConverters()) current.add(describe(converter));
        model.put("converters",current);
        List<Object> catalogue = catalogue();
        model.put("catalogue",catalogue);
        return JSON.writeValueAsString(model);
    }

    public static List<Object> catalogue() throws Exception {
        List<Object> catalogue = new ArrayList<Object>();
        for (String name : NAMES) {
            Class<?> type=Class.forName(PREFIX+name);
            try { type.getConstructor(); } catch (NoSuchMethodException e) { continue; }
            Map<String,Object> item=new LinkedHashMap<String,Object>(); item.put("type",name);
            List<Object> fields=new ArrayList<Object>();
            for (PropertyDescriptor p : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
                if(p.getWriteMethod()==null || p.getReadMethod()==null) continue;
                Map<String,Object> field=new LinkedHashMap<String,Object>();field.put("name",p.getName());
                Class<?> t=p.getPropertyType();field.put("complex",!(t==String.class || t.isPrimitive() || Number.class.isAssignableFrom(t) || t==Boolean.class || t.isEnum()));
                if(t.isEnum()) field.put("options",t.getEnumConstants());
                if(t==boolean.class || t==Boolean.class) field.put("options",Arrays.asList("true","false"));
                fields.add(field);
            }
            item.put("fields",fields);catalogue.add(item);
        }
        return catalogue;
    }

    public static Map<String,Object> describe(DataConverter converter) throws Exception {
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("type",converter.getClass().getSimpleName());
        Map<String,Object> props=new LinkedHashMap<String,Object>();
        for(PropertyDescriptor p:Introspector.getBeanInfo(converter.getClass()).getPropertyDescriptors()) {
            if(p.getWriteMethod()==null || p.getReadMethod()==null) continue;
            Object value=p.getReadMethod().invoke(converter);
            if(value instanceof DataConverter) value=describe((DataConverter)value);
            props.put(p.getName(),value);
        }
        result.put("properties",props);return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object parse(Definition definition,String field,String input) {
        try {
            if("definitionToConvert".equals(field)) {
                Map<String,Object> data=JSON.readValue(input,Map.class);
                String uuid=(String)data.get("uuid");
                if(uuid==null || uuid.isEmpty()) throw new IllegalArgumentException("Choose a source data definition");
                if(uuid.equals(definition.getUuid())) throw new IllegalArgumentException("A definition cannot convert itself");
                Definition source=DefinitionContext.getDefinitionByUuid(sourceType(definition),uuid);
                if(source==null) throw new IllegalArgumentException("Source definition not found");
                Map<String,Object> mappings=new LinkedHashMap<String,Object>();
                Map<String,Object> submitted=(Map<String,Object>)data.get("mappings");
                for(Parameter p:source.getParameters()) {
                    Object value=submitted==null?null:submitted.get(p.getName());
                    if(value instanceof String && !((String)value).startsWith("${")) {
                        WidgetHandler handler=HandlerUtil.getPreferredHandler(WidgetHandler.class,p.getType());
                        value=handler.parse((String)value,p.getType());
                    }
                    if(value!=null) mappings.put(p.getName(),value);
                }
                return new Mapped(source,mappings);
            }
            List<Map<String,Object>> values=JSON.readValue(input,List.class);
            List<DataConverter> converters=new ArrayList<DataConverter>();
            for(Map<String,Object> value:values) converters.add(parseConverter(value));
            return converters;
        } catch(Exception error) {
            if(error instanceof IllegalArgumentException) throw (IllegalArgumentException)error;
            throw new IllegalArgumentException("Invalid converter configuration: "+error.getMessage(),error);
        }
    }

    @SuppressWarnings("unchecked")
    private static DataConverter parseConverter(Map<String,Object> data) throws Exception {
        String name=(String)data.get("type");
        if(!NAMES.contains(name)) throw new IllegalArgumentException("Unknown converter type");
        Class<?> type=Class.forName(PREFIX+name);DataConverter converter=(DataConverter)type.newInstance();
        Map<String,Object> properties=(Map<String,Object>)data.get("properties");
        if(properties==null) return converter;
        Map<String,PropertyDescriptor> fields=new HashMap<String,PropertyDescriptor>();
        for(PropertyDescriptor p:Introspector.getBeanInfo(type).getPropertyDescriptors()) if(p.getWriteMethod()!=null) fields.put(p.getName(),p);
        for(Map.Entry<String,Object> entry:properties.entrySet()) {
            PropertyDescriptor p=fields.get(entry.getKey());
            if(p==null) throw new IllegalArgumentException("Unknown converter property: "+entry.getKey());
            Object value=entry.getValue();if(value==null && p.getPropertyType().isPrimitive()) continue;
            if(value instanceof String && ((String)value).isEmpty() && p.getPropertyType()!=String.class) continue;
            if(value!=null && DataConverter.class.isAssignableFrom(p.getPropertyType())) value=parseConverter((Map<String,Object>)value);
            else if(value!=null) value=JSON.convertValue(value,JSON.getTypeFactory().constructType(p.getWriteMethod().getGenericParameterTypes()[0]));
            p.getWriteMethod().invoke(converter,value);
        }
        return converter;
    }
}
