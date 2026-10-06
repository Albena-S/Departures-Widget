# Change: brand the existing app as "Paris" (small change)

There will be two separate apps on the same phone: this Paris (IDFM) app and a new Sofia app. Make the Paris one instantly recognisable **everywhere outside the widget**. **Do not change the widget's content or layout.**

Use **frontend-design** for the icon, and **verification-before-completion**: check it on the user's phone with screenshots of the app drawer, the widget picker and recent apps.

## 1. Names

- App name (`app_name`): **"Departures Paris"**. Starting with the shared word keeps both apps next to each other in the alphabetical app drawer, and the city is the distinguishing word. Check that it isn't truncated under the icon on the user's phone; if it is, use **"Paris Departures"** or just **"Paris Transit"**.
- Widget picker label and description: **"Departures · Paris"** / "Live departures for your Paris groups".
- App bar title on the Groups screen: "Departures · Paris".
- **Do not change** the `applicationId`, package, or database name. The app must update in place without losing the user's groups. Changing these would install it as a new app.

## 2. Launcher icon

The icon must tell the two apps apart at a glance, **including with Android 13+ themed (monochrome) icons**, where colours are removed. So the difference can't rely on colour alone.

- **A shared base** for both apps, the same in both. For example, a simple departure-board or clock-and-track glyph.
- **City marker:**
  - Paris: a bold **"P"** tag, or a minimal Eiffel Tower silhouette, in the bottom-right corner of the glyph. Background colour: IDFM-style teal/blue.
  - Sofia (built in the Sofia app, specified here so they match): a bold **"S"** tag, or a minimal Alexander Nevsky Cathedral dome silhouette, in the same position. Background colour: Sofia Traffic red (`#BD202E`).
- Pick **letter or silhouette** consistently for both. Default to the **letter**: it's more legible at small sizes and in monochrome.
- Build it as an adaptive icon (`mipmap-anydpi-v26`) with foreground, background **and a `monochrome` layer** that includes the city marker. Keep everything inside the safe zone so it survives circle, squircle and teardrop masks.
- Also update the round icon and the Play-style 512 px PNG (useful for sideloading tools).

## 3. Widget picker preview

Update the widget's `previewLayout` (Android 12+) or `previewImage`, so the picker shows the Paris icon/tag next to the preview and the label says Paris. This only affects the picker, not the placed widget.

## Done when

- [ ] The app drawer shows "Departures Paris" with the new icon. It's distinguishable from the Sofia icon with themed icons both on and off.
- [ ] The widget picker shows "Departures · Paris".
- [ ] The app updated in place: existing groups and widgets are still there and working.
- [ ] The placed widget looks exactly as before.
