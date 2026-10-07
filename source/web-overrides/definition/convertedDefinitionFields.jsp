<c:choose>
<c:when test="${p.field.name == 'definitionToConvert'}">
<input type="hidden" id="definitionToConvert" name="parameter.definitionToConvert.value"/>
<label for="definitionToConvert-select">Source data definition</label>
<select id="definitionToConvert-select"><option value="">Choose...</option></select>
<div id="definitionToConvert-mappings"></div>
<textarea id="converted-editor-data" hidden="hidden"><c:out value="${convertedEditorJson}"/></textarea>
</c:when>
<c:otherwise>
<input type="hidden" id="converters" name="parameter.converters.value"/>
<div id="converters-list"></div>
<label for="converters-type">Converter type</label>
<select id="converters-type"><option value="">Choose...</option></select>
<button id="converters-add" type="button">Add converter</button>
<span id="converters-error" class="error" role="alert"></span>
<script>
$j(function() {
    var config = JSON.parse(document.getElementById('converted-editor-data').value);
    var choice = config.mapped || {}, converters = config.converters || [];
    var select = document.getElementById('definitionToConvert-select');
    function option(parent, value, text) { var o=document.createElement('option');o.value=value;o.textContent=text;parent.appendChild(o); }
    config.definitions.forEach(function(d) {option(select,d.uuid,d.name);});
    select.value=choice.uuid || '';
    function saveMapped() {document.getElementById('definitionToConvert').value=JSON.stringify(choice);}
    function mappings() {
        var target=document.getElementById('definitionToConvert-mappings');target.textContent='';
        var definition=config.definitions.filter(function(d){return d.uuid===choice.uuid;})[0];
        if(!definition) {saveMapped();return;}
        choice.mappings=choice.mappings || {};
        definition.parameters.forEach(function(p,index) {
            var row=document.createElement('div'), label=document.createElement('label'), field=document.createElement('input');
            field.id='definitionToConvert-map-'+index;label.htmlFor=field.id;label.textContent=p.label || p.name;
            field.value=choice.mappings[p.name]==null?'':String(choice.mappings[p.name]);
            field.addEventListener('input',function(){choice.mappings[p.name]=field.value;saveMapped();});
            row.appendChild(label);row.appendChild(field);target.appendChild(row);
        });saveMapped();
    }
    select.addEventListener('change',function(){choice={uuid:select.value,mappings:{}};mappings();});mappings();
    var types=document.getElementById('converters-type');config.catalogue.forEach(function(c){option(types,c.type,c.type.replace(/Converter$/,''));});
    function saveConverters() {document.getElementById('converters').value=JSON.stringify(converters);}
    function renderConverters() {
        var target=document.getElementById('converters-list');target.textContent='';
        converters.forEach(function(converter,index) {
            var row=document.createElement('fieldset'), title=document.createElement('legend');title.textContent=converter.type;row.appendChild(title);
            var schema=config.catalogue.filter(function(c){return c.type===converter.type;})[0];
            if(!schema) {var warning=document.createElement('p');warning.textContent='Existing converter is preserved. Remove it to replace it.';row.appendChild(warning);}
            else schema.fields.forEach(function(spec) {
                var line=document.createElement('div'),label=document.createElement('label'),field=document.createElement(spec.options?'select':spec.complex?'textarea':'input');
                field.id='converters-'+index+'-'+spec.name;label.htmlFor=field.id;label.textContent=spec.name+(spec.complex?' (JSON)':'');
                if(spec.options) {option(field,'','Default');spec.options.forEach(function(value){option(field,String(value),String(value));});}
                var value=(converter.properties || {})[spec.name];field.value=value==null?'':spec.complex?JSON.stringify(value):String(value);
                field.addEventListener('input',function(){
                    try {var parsed=spec.complex && field.value?JSON.parse(field.value):field.value;converter.properties=converter.properties || {};converter.properties[spec.name]=parsed;field.setCustomValidity('');document.getElementById('converters-error').textContent='';saveConverters();}
                    catch(error) {field.setCustomValidity('Enter valid JSON');document.getElementById('converters-error').textContent='Enter valid JSON for '+spec.name;}
                });line.appendChild(label);line.appendChild(field);row.appendChild(line);
            });
            var remove=document.createElement('button');remove.type='button';remove.textContent='Remove converter';remove.addEventListener('click',function(){converters.splice(index,1);renderConverters();});row.appendChild(remove);
            target.appendChild(row);
        });saveConverters();
    }
    document.getElementById('converters-add').addEventListener('click',function(){if(types.value){converters.push({type:types.value,properties:{}});renderConverters();}});
    renderConverters();
    if(choice.uuid) {$j('#selectValuedefinitionToConvert').val('f');$j('#fixedValuedefinitionToConvert').show();}
    if(converters.length) {$j('#selectValueconverters').val('f');$j('#fixedValueconverters').show();}
});
</script>
</c:otherwise>
</c:choose>
