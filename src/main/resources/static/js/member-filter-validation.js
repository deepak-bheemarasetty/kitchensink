const memberFilterForm = document.querySelector(".data-table-toolbar");

if (memberFilterForm) {
  const memberFilter = memberFilterForm.elements.namedItem("email");
  const memberFilterError = document.getElementById("member-filter-error");

  function validateMemberFilter() {
    const length = memberFilter.value.trim().length;
    const invalid = length > 0 && length < 4;
    memberFilterError.textContent = invalid ? "Enter at least 4 characters to filter members." : "";
    memberFilter.setAttribute("aria-invalid", invalid ? "true" : "false");
    return !invalid;
  }

  memberFilter.addEventListener("input", validateMemberFilter);
  memberFilterForm.addEventListener("submit", event => {
    if (!validateMemberFilter()) {
      event.preventDefault();
      memberFilter.focus();
    }
  });
}
