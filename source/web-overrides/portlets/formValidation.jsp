<script type="text/javascript">
// Preserve the existing dialog and entered values when a server-side guard rejects a save.
function reportingRetainForm(form, validate, onSuccess) {
    var errorId = form.id + '-validation';
    var message = document.createElement('div');
    message.id = errorId; message.className = 'error'; message.setAttribute('role', 'alert');
    message.style.display = 'none'; form.insertBefore(message, form.firstChild);
    function show(error) {
        message.textContent = error.message; message.style.display = 'block';
        var field = error.field || form.querySelector('[name=configuration]') || form.querySelector('input:not([type=hidden])');
        if (field) { field.setAttribute('aria-invalid', 'true'); field.setAttribute('aria-describedby', errorId); field.focus(); }
    }
    $j(form).submit(function(event) {
        message.style.display = 'none';
        $j(form).find('[aria-invalid=true]').removeAttr('aria-invalid').removeAttr('aria-describedby');
        var error = validate(form);
        if (error) { event.preventDefault(); show(error); return false; }
        if (!onSuccess) return true;
        event.preventDefault();
        $j.ajax({url: form.action, type: 'POST', data: $j(form).serialize(), dataType: 'text',
            success: function() { onSuccess(); },
            error: function(xhr) {
                var text = 'Unable to save these values. Please correct the form and try again.';
                if (xhr.status === 400 || xhr.status === 404) {
                    var doc = new DOMParser().parseFromString(xhr.responseText, 'text/html');
                    var paragraphs = doc.querySelectorAll('p');
                    for (var i=0; i<paragraphs.length; i++) {
                        if (/^Message\s*/.test(paragraphs[i].textContent)) text = paragraphs[i].textContent.replace(/^Message\s*/, '');
                    }
                }
                show({message:text});
            }
        });
        return false;
    });
}
</script>
