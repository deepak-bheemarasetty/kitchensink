const membersTable = document.getElementById("members-table");

if (membersTable) {
  const tbody = membersTable.querySelector("tbody");
  const rows = Array.from(tbody.querySelectorAll("tr")).filter(row => !row.querySelector("td[colspan]"));
  const sortButtons = membersTable.querySelectorAll("[data-sort-key]");
  let sortKey = "";
  let sortDirection = 1;

  rows.forEach(row => {
    row.dataset.memberRow = "true";
    row.dataset.name = row.cells[0]?.textContent.trim().toLowerCase() ?? "";
    row.dataset.email = row.cells[1]?.textContent.trim().toLowerCase() ?? "";
  });

  function renderRows() {
    const visibleRows = [...rows];
    if (sortKey) {
      visibleRows.sort((a, b) =>
        a.dataset[sortKey].localeCompare(b.dataset[sortKey]) * sortDirection
      );
    }

    visibleRows.forEach(row => tbody.appendChild(row));
  }

  sortButtons.forEach(button => button.addEventListener("click", () => {
    const nextKey = button.dataset.sortKey;
    sortDirection = sortKey === nextKey ? sortDirection * -1 : 1;
    sortKey = nextKey;
    sortButtons.forEach(item => item.classList.toggle("active", item === button));
    renderRows();
  }));
}
