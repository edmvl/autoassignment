(function (AJS) {
    'use strict';

    var $ = AJS.$;
    var REST = AJS.contextPath() + '/rest/autoassignment/1.0';

    function getData(path) {
        return $.ajax({ url: path, dataType: 'json' }).then(function (data) {
            return data;
        });
    }

    function request(options) {
        return $.ajax($.extend({
            contentType: 'application/json',
            dataType: 'json',
            headers: { 'X-Atlassian-Token': 'no-check' }
        }, options));
    }

    function errorText(xhr) {
        try {
            var data = JSON.parse(xhr.responseText);
            if (data && data.message) {
                return data.message;
            }
        } catch (e) {
            // not a JSON error body
        }
        return xhr.responseText || xhr.statusText || xhr.status;
    }

    function showMessage(type, text) {
        $('#message').attr('class', 'aui-message ' + type).html('<p>' + text + '</p>').show();
    }

    function init() {
        if (!$('#save-config-btn').length) {
            return;
        }

        $('#save-config-btn').on('click', save);

        $.when(
            getData(REST + '/fields'),
            getData(REST + '/config')
        ).done(function (fields, config) {
            var $select = $('#coassignee-field');
            (fields || []).forEach(function (field) {
                if (field.type && field.type.indexOf('userpicker') >= 0) {
                    $select.append('<option value="' + field.id + '">' + field.name + '</option>');
                }
            });
            config = config || {};
            if (config.coAssigneeFieldId) {
                $select.val(config.coAssigneeFieldId);
            }
            $('#enabled-toggle').prop('checked', !!config.enabled);
        }).fail(function (xhr) {
            showMessage('error', 'Не удалось загрузить настройки: ' + errorText(xhr));
        });
    }

    function save() {
        var payload = {
            enabled: $('#enabled-toggle').prop('checked'),
            coAssigneeFieldId: $('#coassignee-field').val()
        };
        request({ method: 'PUT', url: REST + '/config', data: JSON.stringify(payload) })
            .done(function () {
                showMessage('success', 'Настройки сохранены.');
            })
            .fail(function (xhr) {
                showMessage('error', 'Не удалось сохранить настройки: ' + errorText(xhr));
            });
    }

    $(document).ready(init);
})(AJS);
