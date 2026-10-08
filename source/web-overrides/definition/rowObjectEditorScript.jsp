<script>
window.reportingObjectEditor = function(config, fields, prefix) {
    function input(name){return fields[name]?document.getElementById(fields[name]):null;}
    function container(name){return fields[name]?document.getElementById('object-editor-'+fields[name]):null;}
    var columns=config.columns || [], filters=config.filters || [], sorts=config.sorts || [];
    var serial=0;
    function el(tag,text){var x=document.createElement(tag);if(text!=null)x.textContent=text;return x;}
    function button(parent,text,fn){var x=el('button',text);x.type='button';x.addEventListener('click',fn);parent.appendChild(x);return x;}
    function field(parent,label,value,change){var wrapper=el('span'), l=el('label',label),input=el('input');input.id=prefix+'-field-'+(++serial);input.value=value==null?'':String(value);l.htmlFor=input.id;input.addEventListener('input',function(){change(input.value);sync();});wrapper.appendChild(l);wrapper.appendChild(input);parent.appendChild(wrapper);return input;}
    function option(parent,value,text){var x=el('option',text);x.value=value;parent.appendChild(x);}
    function select(parent,label,options,value,change){var l=el('label',label),x=el('select');x.id=prefix+'-field-'+(++serial);l.htmlFor=x.id;option(x,'','Choose...');options.forEach(function(o){option(x,o.value,o.label);});x.value=value || '';x.addEventListener('change',function(){change(x.value);sync();});parent.appendChild(l);parent.appendChild(x);return x;}
    function move(items,index,delta,render){var target=index+delta;if(target<0||target>=items.length)return;var value=items.splice(index,1)[0];items.splice(target,0,value);render();sync();}
    function sync(){[['columnDefinitions',columns],['sortCriteria',sorts],['rowFilters',filters]].forEach(function(pair){var node=input(pair[0]);if(node)node.value=JSON.stringify(pair[1]);});}
    function mappings(parent,item,definitions){
        var source=definitions.filter(function(x){return x.uuid===item.uuid;})[0];item.mappings=item.mappings||{};
        if(!source)return;(source.parameters||[]).forEach(function(p){
            if(p.collectionType){var input=field(parent,(p.label||p.name)+' (JSON list)',item.mappings[p.name]==null?'[]':typeof item.mappings[p.name]==='string'?item.mappings[p.name]:JSON.stringify(item.mappings[p.name]),function(v){try{var expression=v.trim().indexOf('$'+'{')===0;var value=expression?v:JSON.parse(v);if(!expression&&!Array.isArray(value))throw new Error();item.mappings[p.name]=value;input.setCustomValidity('');input.removeAttribute('aria-invalid');}catch(e){input.setCustomValidity('Enter a JSON list');input.setAttribute('aria-invalid','true');}});}
            else field(parent,p.label||p.name,item.mappings[p.name],function(v){item.mappings[p.name]=v;});
        });
    }
    function converterFields(parent,column){
        column.converters=column.converters||[];
        column.converters.forEach(function(converter,index){
            var row=el('fieldset'),legend=el('legend',converter.type);row.appendChild(legend);parent.appendChild(row);
            var spec=config.converterCatalogue.filter(function(x){return x.type===converter.type;})[0];converter.properties=converter.properties||{};
            if(spec)(spec.fields||[]).forEach(function(p){
                var value=converter.properties[p.name];
                if(p.options){select(row,p.name,p.options.map(function(x){return{value:String(x),label:String(x)};}),value,function(v){converter.properties[p.name]=v;});}
                else if(p.complex){var label=el('label',p.name+' (JSON)'),area=el('textarea');area.id=prefix+'-field-'+(++serial);label.htmlFor=area.id;area.value=value==null?'':JSON.stringify(value);area.addEventListener('input',function(){try{converter.properties[p.name]=area.value?JSON.parse(area.value):null;area.setCustomValidity('');area.removeAttribute('aria-invalid');sync();}catch(e){area.setCustomValidity('Enter valid JSON');area.setAttribute('aria-invalid','true');}});row.appendChild(label);row.appendChild(area);}
                else field(row,p.name,value,function(v){converter.properties[p.name]=v;});
            });
            button(row,'Remove converter',function(){column.converters.splice(index,1);renderColumns();sync();});
        });
        var chosen='';select(parent,'Converter type',config.converterCatalogue.map(function(x){return{value:x.type,label:x.type};}),'',function(v){chosen=v;});button(parent,'Add converter',function(){if(chosen){column.converters.push({type:chosen,properties:{}});renderColumns();sync();}});
    }
    function renderColumns(){
        var target=container('columnDefinitions');if(!target)return;target.textContent='';
        columns.forEach(function(column,index){
            var row=el('fieldset');row.appendChild(el('legend','Column '+(index+1)));target.appendChild(row);
            field(row,'Column name',column.name,function(v){var old=column.name;column.name=v;sorts.forEach(function(s){if(s.column===old)s.column=v;});renderSorts();});
            var params=el('div');select(row,'Data definition',config.definitions.map(function(x){return{value:x.uuid,label:x.name};}),column.uuid,function(v){column.uuid=v;column.mappings={};params.textContent='';mappings(params,column,config.definitions);});row.appendChild(params);mappings(params,column,config.definitions);
            converterFields(row,column);
            button(row,'Move up',function(){move(columns,index,-1,renderColumns);}).disabled=index===0;
            button(row,'Move down',function(){move(columns,index,1,renderColumns);}).disabled=index===columns.length-1;
            button(row,'Remove column',function(){sorts=sorts.filter(function(s){return s.column!==column.name;});columns.splice(index,1);renderColumns();renderSorts();sync();});
        });button(target,'Add column',function(){columns.push({name:'',uuid:'',mappings:{},converters:[]});renderColumns();sync();});
    }
    function renderSorts(){
        var target=container('sortCriteria');if(!target)return;target.textContent='';
        sorts.forEach(function(sort,index){var row=el('div');target.appendChild(row);select(row,'Sort column',columns.map(function(c){return{value:c.name,label:c.name};}),sort.column,function(v){sort.column=v;});select(row,'Direction',[{value:'ASC',label:'Ascending'},{value:'DESC',label:'Descending'}],sort.direction,function(v){sort.direction=v;});button(row,'Move up',function(){move(sorts,index,-1,renderSorts);}).disabled=index===0;button(row,'Remove sort rule',function(){sorts.splice(index,1);renderSorts();sync();});});button(target,'Add sort rule',function(){sorts.push({column:'',direction:'ASC'});renderSorts();sync();});
    }
    function renderFilters(){
        var target=container('rowFilters');if(!target)return;target.textContent='';
        filters.forEach(function(filter,index){var row=el('fieldset');row.appendChild(el('legend','Row filter '+(index+1)));target.appendChild(row);var params=el('div');select(row,'Row filter definition',config.filterDefinitions.map(function(x){return{value:x.uuid,label:x.name};}),filter.uuid,function(v){filter.uuid=v;filter.mappings={};params.textContent='';mappings(params,filter,config.filterDefinitions);});row.appendChild(params);mappings(params,filter,config.filterDefinitions);button(row,'Remove row filter',function(){filters.splice(index,1);renderFilters();sync();});});button(target,'Add row filter',function(){filters.push({uuid:'',mappings:{}});renderFilters();sync();});
    }
    renderColumns();renderSorts();renderFilters();sync();

    var first=Object.keys(fields).map(input).filter(Boolean)[0];
    if(first)first.form.addEventListener('submit',function(event){
        var invalid=this.querySelector('[id^=object-editor-] :invalid');
        if(invalid){event.preventDefault();invalid.focus();invalid.reportValidity();}
    });
};
</script>
