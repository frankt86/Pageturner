const textLines = (x, y, w, count) => {
  for (let i = 0; i < count; i++) {
    const short = i % 7 === 6;
    line(x, y + i * 24, x + (short ? w * 0.55 : w), y + i * 24, { stroke: LIGHT, strokeWidth: 2 });
  }
};

const readerBar = (x, y, w, title) => {
  icon.back(x + 34, y + 50);
  text(x + 66, y + 56, title, { size: 16, weight: 700 });
  return iconButton(x + w - 42, y + 50, 'volume');
};

// Folded: one page
const p = phone(120, 80, 'Folded');
readerBar(p.x, p.y, 360, 'Echoes of Tomorrow');
textLines(p.x + 28, p.y + 110, 304, 24);
text(p.x + 180, p.y + 730, '12 of 240', { size: 12, fill: GREY, anchor: 'middle' });

// Unfolded: two pages like an open book
const u = { x: 600, y: 80, w: 720, h: 640 };
roundRect(u.x, u.y, u.w, u.h, 30, { strokeWidth: 2.6 });
text(u.x + u.w / 2, u.y - 22, 'Unfolded', { size: 30, font: 'Hand', anchor: 'middle', weight: 600 });
readerBar(u.x, u.y, u.w, 'Echoes of Tomorrow');
const spine = u.x + u.w / 2;
line(spine, u.y + 90, spine, u.y + u.h - 20, { stroke: GREY, strokeWidth: 1.5 });
textLines(u.x + 28, u.y + 110, 300, 21);
textLines(spine + 32, u.y + 110, 300, 21);
text(u.x + u.w / 4, u.y + 612, '12 of 240', { size: 12, fill: GREY, anchor: 'middle' });
text(u.x + (u.w * 3) / 4, u.y + 612, '13 of 240', { size: 12, fill: GREY, anchor: 'middle' });

note(u.x + 120, u.y + u.h + 50, ['Unfold and the book', 'opens to two pages.']);
arrow([[u.x + 330, u.y + u.h + 50], [spine - 10, u.y + u.h + 30], [spine, u.y + u.h - 24]]);

note(u.x + u.w + 30, u.y + 120, ['Reads the book', 'aloud, turning', 'the pages along.']);
arrow([[u.x + u.w + 40, u.y + 100], [u.x + u.w + 20, u.y + 60], [u.x + u.w - 16, u.y + 50]]);

note(p.x - 100, p.y + 820, ['Swipe or tap near the', 'edge to turn a page.']);
arrow([[p.x + 170, p.y + 812], [p.x + 300, p.y + 790], [p.x + 330, p.y + 740]]);
