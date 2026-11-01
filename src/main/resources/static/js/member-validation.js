const memberForm = document.querySelector("form");

if (memberForm) {
  const fields = {
    name: {
      input: memberForm.elements.namedItem("name"),
      error: document.getElementById("name-client-error"),
      validate(value) {
        if (!value) {
          return "Name is required.";
        }
        if (value.length > 25) {
          return "Name must be 25 characters or fewer.";
        }
        if (!/^[\p{L} ]+$/u.test(value)) {
          return "Use letters and spaces only.";
        }
        return "";
      }
    },
    email: {
      input: memberForm.elements.namedItem("email"),
      error: document.getElementById("email-client-error"),
      validate(value, input) {
        if (!value) {
          return "Email is required.";
        }
        if (input.validity.typeMismatch) {
          return "Enter a valid email address.";
        }
        return "";
      }
    },
    phoneNumber: {
      input: memberForm.elements.namedItem("phoneNumber"),
      error: document.getElementById("phoneNumber-client-error"),
      validate(value) {
        if (!value) {
          return "Phone number is required.";
        }
        if (!/^\d{8,12}$/.test(value)) {
          return "Phone number must contain 8 to 12 digits.";
        }
        return "";
      }
    }
  };

  function validateField(field) {
    const message = field.validate(field.input.value, field.input);
    field.error.textContent = message;
    field.input.setAttribute("aria-invalid", String(Boolean(message)));
    return !message;
  }

  Object.values(fields).forEach(field => {
    field.input.addEventListener("input", () => validateField(field));
    field.input.addEventListener("blur", () => validateField(field));
  });

  memberForm.addEventListener("submit", event => {
    const invalidFields = Object.values(fields).filter(field => !validateField(field));
    if (invalidFields.length > 0) {
      event.preventDefault();
      invalidFields[0].input.focus();
    }
  });
}
