(function() {
    "use strict";

    var EPSG_2056_DEF = "+proj=somerc +lat_0=46.95240555555556 +lon_0=7.439583333333333 +k_0=1 +x_0=2600000 +y_0=1200000 +ellps=bessel +towgs84=674.374,15.056,405.346,0,0,0,0 +units=m +no_defs";

    function normalizeKind(rawKind) {
        var kind = (rawKind || "GEOMETRY").toUpperCase();
        if (kind.indexOf("POINT") >= 0) {
            return "POINT";
        }
        if (kind.indexOf("LINE") >= 0) {
            return "LINESTRING";
        }
        if (kind.indexOf("POLYGON") >= 0 || kind.indexOf("SURFACE") >= 0 || kind.indexOf("AREA") >= 0) {
            return "POLYGON";
        }
        return "GEOMETRY";
    }

    function parseSrid(rawSrid) {
        var parsed = parseInt(rawSrid, 10);
        return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
    }

    function registerProjectionIfNeeded(srid) {
        if (srid !== 2056) {
            return;
        }
        if (!window.ol || !window.proj4 || !ol.proj || !ol.proj.proj4 || typeof ol.proj.proj4.register !== "function") {
            return;
        }
        if (!ol.proj.get("EPSG:2056")) {
            proj4.defs("EPSG:2056", EPSG_2056_DEF);
            ol.proj.proj4.register(proj4);
        }
    }

    function resolveProjectionCode(srid) {
        if (srid) {
            registerProjectionIfNeeded(srid);
            var epsgCode = "EPSG:" + srid;
            if (ol.proj.get(epsgCode)) {
                return epsgCode;
            }
            console.warn("Projection not available for", epsgCode, "falling back to EPSG:3857");
        }
        return "EPSG:3857";
    }

    function fitToFeatures(map, source) {
        var extent = source.getExtent();
        if (!extent || ol.extent.isEmpty(extent)) {
            return;
        }
        map.getView().fit(extent, {
            padding: [24, 24, 24, 24],
            duration: 200,
            maxZoom: 18
        });
    }

    function normalizeDrawType(drawType) {
        if (drawType === "Point" || drawType === "LineString" || drawType === "Polygon") {
            return drawType;
        }
        if (!drawType) {
            return "Polygon";
        }
        var normalized = String(drawType).toUpperCase();
        if (normalized.indexOf("POINT") >= 0) {
            return "Point";
        }
        if (normalized.indexOf("LINE") >= 0) {
            return "LineString";
        }
        return "Polygon";
    }

    function resolveFixedDrawType(kind) {
        if (kind === "POINT") {
            return "Point";
        }
        if (kind === "LINESTRING") {
            return "LineString";
        }
        if (kind === "POLYGON") {
            return "Polygon";
        }
        return null;
    }

    function dispatchWktEvents(input) {
        input.dispatchEvent(new Event("input", { bubbles: true }));
        input.dispatchEvent(new Event("change", { bubbles: true }));
    }

    function setupInteractionState(map, source, resolveDrawType, mode) {
        var drawInteraction = null;
        var modifyInteraction = null;

        function removeDraw() {
            if (drawInteraction) {
                map.removeInteraction(drawInteraction);
                drawInteraction = null;
            }
        }

        function enableDraw() {
            if (mode === "view") {
                return;
            }
            removeDraw();
            var resolvedDrawType = normalizeDrawType(resolveDrawType());
            drawInteraction = new ol.interaction.Draw({
                source: source,
                type: resolvedDrawType,
                maxPoints: resolvedDrawType === "Point" ? 1 : undefined
            });
            drawInteraction.on("drawstart", function() {
                source.clear();
            });
            map.addInteraction(drawInteraction);
        }

        function disableDrawAndEnableModify() {
            removeDraw();
            if (!modifyInteraction || mode === "view") {
                return;
            }
            modifyInteraction.setActive(true);
        }

        function clearGeometry() {
            source.clear();
        }

        if (mode !== "view") {
            modifyInteraction = new ol.interaction.Modify({ source: source });
            map.addInteraction(modifyInteraction);
        }

        return {
            enableDraw: enableDraw,
            disableDrawAndEnableModify: disableDrawAndEnableModify,
            clearGeometry: clearGeometry,
            removeDraw: removeDraw
        };
    }

    function resizeMapSoon(map, source) {
        window.setTimeout(function() {
            map.updateSize();
            fitToFeatures(map, source);
        }, 80);
    }

    function initEditor(editor) {
        var wktInput = editor.querySelector(".js-geometry-wkt");
        var mapElement = editor.querySelector(".ili-geometry-map");
        if (!wktInput || !mapElement) {
            return;
        }

        var mode = editor.dataset.geometryMode || "edit";
        var kind = normalizeKind(editor.dataset.geometryKind);
        var fixedDrawType = resolveFixedDrawType(kind);
        var srid = parseSrid(editor.dataset.geometrySrid);
        var dataProjection = resolveProjectionCode(srid);
        var viewProjection = "EPSG:3857";
        var typePicker = editor.querySelector(".js-geometry-type-picker");
        var typeSelect = editor.querySelector(".js-geometry-draw-type");

        var source = new ol.source.Vector();
        var vectorLayer = new ol.layer.Vector({ source: source });
        var map = new ol.Map({
            target: mapElement,
            layers: [
                new ol.layer.Tile({ source: new ol.source.OSM() }),
                vectorLayer
            ],
            view: new ol.View({
                center: ol.proj.fromLonLat([8.2319736, 46.7985624]),
                zoom: 8,
                projection: viewProjection
            })
        });

        var format = new ol.format.WKT();

        function writeWkt() {
            var features = source.getFeatures();
            if (!features.length) {
                wktInput.value = "";
                dispatchWktEvents(wktInput);
                return;
            }
            var geometry = features[0].getGeometry().clone();
            if (dataProjection !== viewProjection) {
                geometry.transform(viewProjection, dataProjection);
            }
            wktInput.value = format.writeGeometry(geometry);
            dispatchWktEvents(wktInput);
        }

        function readInitialWkt() {
            var raw = (wktInput.value || "").trim();
            if (!raw) {
                return;
            }
            try {
                var feature = format.readFeature(raw, {
                    dataProjection: dataProjection,
                    featureProjection: viewProjection
                });
                source.clear();
                source.addFeature(feature);
                fitToFeatures(map, source);
            } catch (error) {
                console.warn("Could not parse WKT:", error);
            }
        }

        source.on("addfeature", function(event) {
            var features = source.getFeatures();
            if (features.length > 1) {
                source.removeFeature(features[0] === event.feature ? features[1] : features[0]);
            }
            writeWkt();
            fitToFeatures(map, source);
        });
        source.on("changefeature", writeWkt);
        source.on("removefeature", writeWkt);

        if (typeSelect) {
            if (fixedDrawType) {
                typeSelect.value = fixedDrawType;
                typeSelect.disabled = true;
                if (typePicker) {
                    typePicker.hidden = true;
                }
            } else {
                if (!typeSelect.value) {
                    typeSelect.value = "Polygon";
                }
                typeSelect.disabled = mode === "view";
                if (typePicker) {
                    typePicker.hidden = mode === "view";
                }
            }
        }

        function resolveDrawType() {
            if (fixedDrawType) {
                return fixedDrawType;
            }
            if (typeSelect && typeSelect.value) {
                return typeSelect.value;
            }
            return "Polygon";
        }

        var interactionState = setupInteractionState(map, source, resolveDrawType, mode);

        var drawButton = editor.querySelector('[data-geometry-action="draw"]');
        var modifyButton = editor.querySelector('[data-geometry-action="modify"]');
        var clearButton = editor.querySelector('[data-geometry-action="clear"]');

        if (drawButton) {
            drawButton.addEventListener("click", interactionState.enableDraw);
        }
        if (modifyButton) {
            modifyButton.addEventListener("click", interactionState.disableDrawAndEnableModify);
        }
        if (clearButton) {
            clearButton.addEventListener("click", interactionState.clearGeometry);
        }
        if (typeSelect && !fixedDrawType) {
            typeSelect.addEventListener("change", interactionState.removeDraw);
        }

        if (mode === "view") {
            if (drawButton) {
                drawButton.disabled = true;
            }
            if (modifyButton) {
                modifyButton.disabled = true;
            }
            if (clearButton) {
                clearButton.disabled = true;
            }
        }

        editor.addEventListener("ili:geometry-panel-visible", function() {
            resizeMapSoon(map, source);
        });

        readInitialWkt();
        resizeMapSoon(map, source);
    }

    function setupTabPanels(root) {
        var tabContainers = root.querySelectorAll(".js-geometry-tabs");
        tabContainers.forEach(function(tabs) {
            var panelHost = tabs.closest(".ili-map-panel");
            if (!panelHost) {
                return;
            }
            var panels = panelHost.querySelectorAll(".js-geometry-tab-panel");
            var tabItems = tabs.querySelectorAll("bx-tab");
            if (!panels.length || !tabItems.length) {
                return;
            }

            function activate(value) {
                if (!value) {
                    return;
                }
                panels.forEach(function(panel) {
                    var active = panel.dataset.geometryPanel === value;
                    panel.classList.toggle("is-active", active);
                    panel.hidden = !active;
                    if (active) {
                        panel.querySelectorAll(".ili-geometry-editor").forEach(function(editor) {
                            editor.dispatchEvent(new CustomEvent("ili:geometry-panel-visible", { bubbles: true }));
                        });
                    }
                });
                tabItems.forEach(function(tab) {
                    if (tab.getAttribute("value") === value) {
                        tab.setAttribute("selected", "");
                    } else {
                        tab.removeAttribute("selected");
                    }
                });
            }

            function selectedValueFromTabs() {
                var selected = tabs.querySelector("bx-tab[selected]");
                if (selected) {
                    return selected.getAttribute("value");
                }
                if (tabItems.length) {
                    return tabItems[0].getAttribute("value");
                }
                return null;
            }

            tabs.addEventListener("click", function(event) {
                var tab = event.target.closest("bx-tab");
                if (!tab) {
                    return;
                }
                activate(tab.getAttribute("value"));
            });

            tabs.addEventListener("bx-tabs-selected", function() {
                activate(selectedValueFromTabs());
            });

            activate(selectedValueFromTabs());
        });
    }

    document.addEventListener("DOMContentLoaded", function() {
        if (!window.ol) {
            console.warn("OpenLayers not available. Geometry editor disabled.");
            return;
        }
        var editors = document.querySelectorAll(".ili-geometry-editor");
        editors.forEach(initEditor);
        setupTabPanels(document);
    });
})();
