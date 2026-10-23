const p = phone(300, 80, 'Library');
const { x, y } = p;

text(x + 24, y + 70, 'Good evening', { size: 28 });

const audioHeader = text(x + 20, y + 130, 'Current', { size: 20 });
roundRect(audioHeader.x + audioHeader.width + 10, y + 113, 30, 22, 11, { stroke: GREY });
text(audioHeader.x + audioHeader.width + 25, y + 129, '1', { size: 12, weight: 700, anchor: 'middle' });
roundRect(x + 16, y + 146, 328, 84, 18, { stroke: GREY });
image(x + 26, y + 156, 64, 64, 12);
text(x + 106, y + 184, 'Echoes of Tomorrow', { size: 15 });
text(x + 106, y + 204, 'Daniel Hartwell', { size: 12, fill: GREY });

const header = text(x + 20, y + 290, 'Ebooks', { size: 20 });
roundRect(header.x + header.width + 10, y + 273, 30, 22, 11, { stroke: GREY });
text(header.x + header.width + 25, y + 289, '3', { size: 12, weight: 700, anchor: 'middle' });

const ebooks = [
  ['The Lantern Keepers', 'EPUB', 0.4],
  ['The Quiet Orbit', 'EPUB', 0],
  ['Field Guide to Stars', 'PDF', 0.8],
];
ebooks.forEach(([title, format, progress], i) => {
  const ry = y + 306 + i * 86;
  roundRect(x + 16, ry, 328, 84, 18, { stroke: GREY });
  roundRect(x + 26, ry + 10, 64, 64, 12);
  path(`M${x + 46},${ry + 28} H${x + 70} V${ry + 58} H${x + 46} Z`);
  line(x + 52, ry + 28, x + 52, ry + 58);
  text(x + 106, ry + 36, title, { size: 15 });
  if (progress > 0) {
    line(x + 106, ry + 58, x + 106 + 160 * progress, ry + 58, { strokeWidth: 4 });
    line(x + 106 + 160 * progress + 4, ry + 58, x + 266, ry + 58, { stroke: LIGHT, strokeWidth: 4 });
  }
  text(x + 330, ry + 62, format, { size: 12, fill: GREY, anchor: 'end' });
});

note(x + 400, y + 300, ['EPUB and PDF files', 'in your library folders', 'get their own section.']);
arrow([[x + 396, y + 320], [x + 330, y + 270], [header.x + header.width + 50, y + 284]]);

note(x + 400, y + 520, ['Tap to read,', 'right where you', 'left off.']);
arrow([[x + 396, y + 540], [x + 370, y + 560], [x + 346, y + 520]]);
