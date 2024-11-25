document.addEventListener('DOMContentLoaded', function() {
    const plotSelector = document.querySelector('.plot-selector');
    plotSelector.addEventListener('change', function(e) {
        window.location.hash = e.target.value;
    });
});
