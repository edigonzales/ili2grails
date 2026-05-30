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

    document.addEventListener("DOMContentLoaded", function() {
        document.querySelectorAll(".js-dirty-form").forEach(initDirtyForm);
        initBeforeUnloadGuard();
        initSubmitButtons();
        initUnsavedNavigationGuard();
        initDeleteModal();
    });
})();
