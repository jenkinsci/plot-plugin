document.addEventListener('DOMContentLoaded', function () {
    const plotSelector = document.querySelector('.plot-selector');
    if (plotSelector) {
        plotSelector.addEventListener('change', function () {
            window.location.hash = this.value;
        });
    }
});
