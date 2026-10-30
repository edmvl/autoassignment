(function (AJS) {
    'use strict';

    var $ = AJS.$;
    var REST = AJS.contextPath() + '/rest/autoassignment/1.0';

    var allFields = [];
    var userFields = [];

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

    function isUserField(field) {
        return !!(field && field.type && field.type.indexOf('userpicker') >= 0);
    }

    function sortFields(fields) {
        return fields.slice().sort(function (a, b) {
            return String(a.name || a.id).localeCompare(String(b.name || b.id), 'ru');
        });
    }

    function renderFieldList(selectedIds) {
        var $list = $('#field-list');
        $list.empty();
        var selected = {};
        (selectedIds || []).forEach(function (id) {
            selected[id] = true;
        });
        if (!allFields.length) {
            $list.append('<div class="field-list-empty">Нет доступных полей</div>');
            updateCount();
            return;
        }
        sortFields(allFields).forEach(function (field) {
            var $label = $('<label class="field-list-item"></label>')
                .attr('data-search', (String(field.name || '') + ' ' + field.id).toLowerCase());
            var $checkbox = $('<input type="checkbox">')
                .val(field.id)
                .prop('checked', !!selected[field.id]);
            $label.append($checkbox);
            $label.append(document.createTextNode(' ' + field.name));
            $list.append($label);
        });
        updateCount();
    }

    function visibleCheckboxes() {
        return $('#field-list .field-list-item:visible input[type=checkbox]');
    }

    function updateCount() {
        var total = $('#field-list input[type=checkbox]').length;
        var checked = $('#field-list input[type=checkbox]:checked').length;
        $('#field-list-count').text('Выбрано: ' + checked + ' из ' + total);
    }

    function init() {
        if (!$('#save-config-btn').length) {
            return;
        }

        $('#save-config-btn').on('click', save);

        $('#field-search').on('input', function () {
            var term = String($(this).val() || '').toLowerCase();
            $('#field-list .field-list-item').each(function () {
                var $item = $(this);
                var search = $item.attr('data-search') || '';
                $item.toggle(search.indexOf(term) >= 0);
            });
        });

        $('#fields-select-all').on('click', function () {
            visibleCheckboxes().prop('checked', true);
            updateCount();
        });

        $('#fields-clear-all').on('click', function () {
            visibleCheckboxes().prop('checked', false);
            updateCount();
        });

        $('#field-list').on('change', 'input[type=checkbox]', updateCount);

        // The full (unfiltered) field list is only available to administrators.
        $.when(
            getData(REST + '/fields?all=true'),
            getData(REST + '/config')
        ).done(function (fields, config) {
            allFields = fields || [];
            userFields = allFields.filter(isUserField);

            var $select = $('#coassignee-field');
            sortFields(userFields).forEach(function (field) {
                $select.append($('<option></option>').val(field.id).text(field.name));
            });

            config = config || {};
            if (config.coAssigneeFieldId) {
                $select.val(config.coAssigneeFieldId);
            }
            $('#enabled-toggle').prop('checked', !!config.enabled);

            // null = never configured -> all fields are allowed by default.
            var selected = config.conditionFieldIds;
            if (selected === null || selected === undefined) {
                selected = allFields.map(function (field) { return field.id; });
            }
            renderFieldList(selected);
        }).fail(function (xhr) {
            showMessage('error', 'Не удалось загрузить настройки: ' + errorText(xhr));
        });
    }

    function save() {
        var conditionFieldIds = $('#field-list input[type=checkbox]:checked').map(function () {
            return this.value;
        }).get();
        var payload = {
            enabled: $('#enabled-toggle').prop('checked'),
            coAssigneeFieldId: $('#coassignee-field').val(),
            conditionFieldIds: conditionFieldIds
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
