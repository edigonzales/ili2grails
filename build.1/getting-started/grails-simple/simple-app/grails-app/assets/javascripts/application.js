// This is a manifest file that'll be compiled into application.js.
//
// Any JavaScript file within this directory can be referenced here using a relative path.
//
// You're free to add application-wide JavaScript to this file, but it's generally better
// to create separate JavaScript files as needed.
//
//= require webjars/jquery/%/dist/jquery.js
//= require webjars/bootstrap/%/dist/js/bootstrap.bundle.js
//= require webjars/proj4/2.11.0/dist/proj4.js
//= require webjars/ol/9.2.4/dist/ol.js
//= require webjars/bootstrap/5.3.3/js/bootstrap.bundle.min.js
//= require ili-geometry-editor.js
//= require ili-form-ux.js
//= require ili-navigation.js
//= require_self

if (typeof jQuery !== 'undefined') {
    (function($) {
        $('#spinner').ajaxStart(function() {
            $(this).fadeIn();
        }).ajaxStop(function() {
            $(this).fadeOut();
        });
    })(jQuery);
}