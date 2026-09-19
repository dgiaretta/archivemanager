// Filters the <option>s of a <select> as you type into a paired text input.
// Wire-up: <input class="select-filter" data-target="someSelectId"> placed
// anywhere on the page, paired with <select id="someSelectId">. Works with
// <optgroup>-organized selects: a group hides itself once none of its
// options match, and the "-- choose --" placeholder (value="") always stays
// visible regardless of the filter term.
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.select-filter').forEach(function (input) {
        var select = document.getElementById(input.getAttribute('data-target'));
        if (!select) {
            return;
        }

        input.addEventListener('input', function () {
            var term = input.value.trim().toLowerCase();

            select.querySelectorAll('option').forEach(function (opt) {
                if (opt.value === '') {
                    return; // always keep the placeholder option visible
                }
                var match = term === '' || opt.textContent.toLowerCase().indexOf(term) !== -1;
                opt.hidden = !match;
            });

            select.querySelectorAll('optgroup').forEach(function (group) {
                var anyVisible = Array.prototype.some.call(
                    group.querySelectorAll('option'),
                    function (opt) { return !opt.hidden; }
                );
                group.hidden = !anyVisible;
            });
        });

        // Enter should never submit the surrounding form while filtering --
        // it should just keep narrowing the list.
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault();
            }
        });
    });
});
