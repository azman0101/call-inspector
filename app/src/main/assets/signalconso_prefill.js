// Prefills the SignalConso "Démarchage abusif" wizard from window.__icPlan, and step 4 ("Vos coordonnées")
// from window.__icContact, the details the user saved on the phone. It only selects options and
// fills empty fields, each at most once, and never clicks "Suivant", "Continuer" or the final submit:
// the user reviews every step and moves on. The only button it presses is the company search
// "Rechercher", which looks the company up without submitting anything; a SIRET search returning
// exactly that company gets it selected.
(function () {
  if (window.__icPrefillInstalled) return;
  window.__icPrefillInstalled = true;
  var done = {};

  function norm(s) {
    return (s || '').replace(/[’]/g, "'").replace(/\s+/g, ' ').trim().toLowerCase();
  }

  function pickRadio(title) {
    if (!title || done['radio:' + title]) return;
    var labels = document.querySelectorAll('label');
    for (var i = 0; i < labels.length; i++) {
      if (norm(labels[i].innerText).indexOf(norm(title)) !== 0) continue;
      var input = labels[i].htmlFor ? document.getElementById(labels[i].htmlFor) : labels[i].querySelector('input');
      if (input && !input.checked) labels[i].click();
      done['radio:' + title] = true;
      return;
    }
  }

  // React tracks the native value setter: assign through it, then emit input/change so its state follows.
  function setValue(el, value) {
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function fillOnce(key, el, value) {
    if (!el || !value || done[key]) return;
    done[key] = true;
    setValue(el, value);
  }

  // Step 4 ("Vos coordonnées") also has an input[name=phone], for the user's own number:
  // only target the one labelled as the number that called.
  function callerPhoneInput() {
    var inputs = document.querySelectorAll('input[name="phone"]');
    for (var i = 0; i < inputs.length; i++) {
      var label = inputs[i].id && document.querySelector('label[for="' + inputs[i].id + '"]');
      if (label && norm(label.innerText).indexOf('vous ayant contacté') !== -1) return inputs[i];
    }
    return null;
  }

  // Step 2 company identification: pick "by SIRET" or "by name", fill the field it reveals and run the
  // search once, so the user only has to pick the matching result.
  function searchCompany(radioTitle, fieldName, value) {
    pickRadio(radioTitle);
    if (!done['radio:' + radioTitle] || done['company']) return;
    var input = document.querySelector('input[name="' + fieldName + '"]');
    if (!input) return;
    fillOnce('company', input, value);
    setTimeout(function () {
      for (var node = input.parentElement; node; node = node.parentElement) {
        var buttons = node.querySelectorAll('button');
        for (var i = 0; i < buttons.length; i++) {
          if (norm(buttons[i].innerText) === 'rechercher') { buttons[i].click(); return; }
        }
      }
    }, 300);
  }

  // Step 4 also has input[name=phone], for the user's own number ("Téléphone (facultatif)").
  function contactPhoneInput() {
    var inputs = document.querySelectorAll('input[name="phone"]');
    for (var i = 0; i < inputs.length; i++) {
      var label = inputs[i].id && document.querySelector('label[for="' + inputs[i].id + '"]');
      if (label && norm(label.innerText).indexOf('vous ayant contacté') === -1) return inputs[i];
    }
    return null;
  }

  // Saved contact details go into empty fields only, and never twice into the same field: a value the
  // user erased stays erased. SignalConso rebuilds step 4 empty after "Précédent", which gets refilled.
  function fillContact(contact) {
    var fields = {
      firstName: document.querySelector('input[name="firstName"]'),
      lastName: document.querySelector('input[name="lastName"]'),
      email: document.querySelector('input[name="email"]'),
      phone: contactPhoneInput(),
      referenceNumber: document.querySelector('input[name="referenceNumber"]')
    };
    for (var key in fields) {
      var el = fields[key], value = contact[key];
      if (!el || !value || el.value || el.__icContactFilled === value) continue;
      el.__icContactFilled = value;
      setValue(el, value);
    }
    if (contact.shareContact === true || contact.shareContact === false) {
      pickShareChoice(contact.shareContact ? 'Je partage mes coordonnées' : 'Je ne partage pas mes coordonnées');
    }
  }

  // "Souhaitez-vous partager vos coordonnées avec l'entreprise ?": answered only while neither option is
  // checked, so a choice the user makes is kept (and a step 4 rebuilt empty gets it again).
  function pickShareChoice(title) {
    var labels = document.querySelectorAll('label');
    for (var i = 0; i < labels.length; i++) {
      if (norm(labels[i].innerText).indexOf(norm(title)) !== 0) continue;
      var input = labels[i].htmlFor ? document.getElementById(labels[i].htmlFor) : labels[i].querySelector('input');
      if (!input || input.__icContactFilled) return;
      var group = input.name ? document.querySelectorAll('input[type="radio"][name="' + input.name + '"]') : [input];
      for (var j = 0; j < group.length; j++) if (group[j].checked) return;
      input.__icContactFilled = true;
      labels[i].click();
      return;
    }
  }

  // A SIRET/SIREN search returns the matching establishment(s): select it only when there is a single
  // one carrying that number. A name search is left to the user, its results are too loose.
  function selectSiretResult(siret) {
    if (done['companyResult']) return;
    var radios = document.querySelectorAll('#CompanySearchResult input[type="radio"]');
    if (!radios.length) return;
    done['companyResult'] = true;
    if (radios.length !== 1) return;
    var label = radios[0].id && document.querySelector('label[for="' + radios[0].id + '"]');
    var digits = label ? label.innerText.replace(/\s+/g, '') : '';
    if (digits.indexOf('SIRET' + siret) !== -1 && !radios[0].checked) label.click();
  }

  function run() {
    var plan = window.__icPlan;
    if (!plan) return;
    pickRadio(plan.problem);
    pickRadio(plan.subcategory);
    fillOnce('phone', callerPhoneInput(), plan.phone);
    if (plan.company && plan.company.siret) {
      searchCompany('Par son numéro SIRET', 'identity', plan.company.siret);
      selectSiretResult(plan.company.siret);
    }
    else if (plan.company) searchCompany('Par son nom', 'name', plan.company.name);
    var dates = document.querySelectorAll('input[type="date"]');
    for (var i = 0; i < dates.length && plan.dates && i < plan.dates.length; i++) {
      fillOnce('date:' + i, dates[i], plan.dates[i]);
    }
    fillOnce('description', document.querySelector('textarea'), plan.description);
    if (window.__icContact) fillContact(window.__icContact);
  }
  // Lets the app apply contact details saved while the form is open.
  window.__icPrefillRun = run;

  new MutationObserver(run).observe(document.documentElement, { childList: true, subtree: true });
  run();
})();
