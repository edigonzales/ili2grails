(function() {
    "use strict";

    var dirtyForms = new Set();

    function readValue(field) {
        if (!field) {
            return "";
        }
        var tag = field.tagName.toLowerCase();
        if (tag === "input") {
            var type = (field.getAttribute("type") || "text").toLowerCase();
            if (type === "checkbox" || type === "radio") {
                return field.checked ? "1" : "0";
            }
        }
        return field.value || "";
    }

    function setDirtyState(form, dirty) {
        if (dirty) {
            dirtyForms.add(form);
        } else {
            dirtyForms.delete(form);
        }
        form.dataset.dirty = dirty ? "true" : "false";

        var page = form.closest(".ili-page");
        var badge = page ? page.querySelector("[data-unsaved-badge]") : null;
        if (badge) {
            badge.hidden = !dirty;
        }
    }

    function evaluateDirty(form, initialValues, trackedFields) {
        var dirty = trackedFields.some(function(field) {
            return readValue(field) !== initialValues.get(field);
        });
        setDirtyState(form, dirty);
    }

    function initDirtyForm(form) {
        var trackedFields = Array.from(form.querySelectorAll("input, select, textarea"))
            .filter(function(field) {
                if (!field.name && !field.classList.contains("js-geometry-wkt")) {
                    return false;
                }
                var type = (field.getAttribute("type") || "").toLowerCase();
                return type !== "submit" && type !== "button";
            });

        if (!trackedFields.length) {
            return;
        }

        var initialValues = new Map();
        trackedFields.forEach(function(field) {
            initialValues.set(field, readValue(field));
            field.addEventListener("input", function() {
                evaluateDirty(form, initialValues, trackedFields);
            });
            field.addEventListener("change", function() {
                evaluateDirty(form, initialValues, trackedFields);
            });
        });

        form.addEventListener("submit", function() {
            setDirtyState(form, false);
        });

        evaluateDirty(form, initialValues, trackedFields);
    }

    function hasDirtyForm() {
        return dirtyForms.size > 0;
    }

    function initBeforeUnloadGuard() {
        window.addEventListener("beforeunload", function(event) {
            if (!hasDirtyForm()) {
                return;
            }
            event.preventDefault();
            event.returnValue = "";
        });
    }

    function submitFormFromAction(event, actionElement) {
        var form = actionElement.closest("form");
        if (!form) {
            return;
        }
        var nativeSubmit = form.querySelector(".js-native-submit");
        event.preventDefault();
        if (nativeSubmit) {
            nativeSubmit.click();
            return;
        }
        if (typeof form.requestSubmit === "function") {
            form.requestSubmit();
            return;
        }
        form.submit();
    }

    function initSubmitButtons() {
        document.addEventListener("click", function(event) {
            var submitAction = event.target.closest("[data-form-submit]");
            if (submitAction) {
                submitFormFromAction(event, submitAction);
                return;
            }

            var rowDeleteAction = event.target.closest("[data-row-delete]");
            if (rowDeleteAction) {
                event.preventDefault();
                if (!window.confirm("Datensatz wirklich löschen?")) {
                    return;
                }
                var rowDeleteFormId = rowDeleteAction.getAttribute("data-delete-form");
                var rowDeleteForm = rowDeleteFormId ? document.getElementById(rowDeleteFormId) : null;
                if (!rowDeleteForm) {
                    return;
                }
                var rowDeleteSubmit = rowDeleteForm.querySelector(".js-delete-submit");
                if (rowDeleteSubmit) {
                    rowDeleteSubmit.click();
                } else {
                    rowDeleteForm.submit();
                }
            }
        });
    }

    function initUnsavedNavigationGuard() {
        document.addEventListener("click", function(event) {
            var navAction = event.target.closest("[data-unsaved-nav]");
            if (!navAction || !hasDirtyForm()) {
                return;
            }
            var proceed = window.confirm("Es gibt ungespeicherte Änderungen. Seite wirklich verlassen?");
            if (!proceed) {
                event.preventDefault();
                event.stopPropagation();
            }
        });
    }

    function hideModal(modalElement) {
        if (!modalElement || !window.bootstrap || !bootstrap.Modal) {
            return;
        }
        var modal = bootstrap.Modal.getInstance(modalElement) || bootstrap.Modal.getOrCreateInstance(modalElement);
        modal.hide();
    }

    function initDeleteModal() {
        document.addEventListener("click", function(event) {
            var confirmAction = event.target.closest("[data-delete-confirm]");
            if (!confirmAction) {
                return;
            }
            event.preventDefault();
            var formId = confirmAction.getAttribute("data-delete-form");
            var form = formId ? document.getElementById(formId) : null;
            hideModal(confirmAction.closest(".modal"));
            if (!form) {
                return;
            }
            var submit = form.querySelector(".js-delete-submit");
            if (submit) {
                submit.click();
            } else {
                form.submit();
            }
        });
    }

    function optionFromData(item) {
        var option = document.createElement("option");
        option.value = item.id || "";
        option.textContent = item.label || item.id || "";
        return option;
    }

    function renderRelationshipOptions(select, results) {
        if (!select) {
            return;
        }
        var previousValue = select.value;
        var previousLabel = "";
        var selectedOption = select.selectedOptions && select.selectedOptions.length ? select.selectedOptions[0] : null;
        if (selectedOption) {
            previousLabel = selectedOption.textContent || "";
        }
        var optional = select.getAttribute("data-relationship-optional") !== "false";
        select.innerHTML = "";
        if (optional) {
            var empty = document.createElement("option");
            empty.value = "";
            empty.textContent = "Keine Auswahl";
            select.appendChild(empty);
        }
        var hasPreviousValue = !previousValue;
        (results || []).forEach(function(item) {
            if (!item || !item.id) {
                return;
            }
            if (item.id === previousValue) {
                hasPreviousValue = true;
            }
            select.appendChild(optionFromData(item));
        });
        if (previousValue && !hasPreviousValue) {
            select.appendChild(optionFromData({
                id: previousValue,
                label: previousLabel || previousValue
            }));
        }
        select.value = previousValue || "";
    }

    function relationshipUrl(input) {
        var url = input.getAttribute("data-relationship-url");
        var field = input.getAttribute("data-relationship-field");
        if (!url || !field) {
            return null;
        }
        var params = new URLSearchParams();
        params.set("field", field);
        params.set("q", input.value || "");
        params.set("max", "25");
        return url + (url.indexOf("?") >= 0 ? "&" : "?") + params.toString();
    }

    function initRelationshipAutocomplete(input) {
        var selectId = input.getAttribute("data-relationship-select");
        var select = selectId ? document.getElementById(selectId) : null;
        if (!select || typeof window.fetch !== "function") {
            return;
        }
        var timer = null;
        input.addEventListener("input", function() {
            window.clearTimeout(timer);
            timer = window.setTimeout(function() {
                var url = relationshipUrl(input);
                if (!url) {
                    return;
                }
                window.fetch(url, {
                    headers: {
                        "Accept": "application/json"
                    }
                })
                    .then(function(response) {
                        return response.ok ? response.json() : null;
                    })
                    .then(function(payload) {
                        if (!payload) {
                            return;
                        }
                        renderRelationshipOptions(select, payload.results || []);
                    })
                    .catch(function() {
                        // Keep the existing server-rendered options if autocomplete fails.
                    });
            }, 250);
        });
    }

    document.addEventListener("DOMContentLoaded", function() {
        document.querySelectorAll(".js-dirty-form").forEach(initDirtyForm);
        document.querySelectorAll(".js-relationship-search").forEach(initRelationshipAutocomplete);
        initBeforeUnloadGuard();
        initSubmitButtons();
        initUnsavedNavigationGuard();
        initDeleteModal();
    });
})();
