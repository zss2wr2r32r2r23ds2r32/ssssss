function colorDistance(data: Uint8ClampedArray, index: number, red: number, green: number, blue: number) {
  return Math.abs(data[index] - red) + Math.abs(data[index + 1] - green) + Math.abs(data[index + 2] - blue);
}

export function clearBackdrop(data: Uint8ClampedArray, width: number, height: number) {
  if (width < 2 || height < 2) return;
  const samples: number[] = [];
  const take = (x: number, y: number) => {
    const index = (y * width + x) * 4;
    if (data[index + 3] > 16) samples.push(index);
  };
  take(0, 0);
  take(width - 1, 0);
  take(0, height - 1);
  take(width - 1, height - 1);
  take(Math.floor(width / 2), 0);
  take(0, Math.floor(height / 2));
  take(width - 1, Math.floor(height / 2));
  take(Math.floor(width / 2), height - 1);
  if (!samples.length) return;

  let red = 0;
  let green = 0;
  let blue = 0;
  for (const index of samples) {
    red += data[index];
    green += data[index + 1];
    blue += data[index + 2];
  }
  red = Math.round(red / samples.length);
  green = Math.round(green / samples.length);
  blue = Math.round(blue / samples.length);

  const removed = new Uint8Array(width * height);
  const queue: number[] = [];
  const mark = (x: number, y: number, fromRed: number, fromGreen: number, fromBlue: number) => {
    if (x < 0 || y < 0 || x >= width || y >= height) return;
    const pixel = y * width + x;
    if (removed[pixel]) return;
    const index = pixel * 4;
    if (data[index + 3] < 12) {
      removed[pixel] = 1;
      queue.push(pixel);
      return;
    }
    const backdrop = colorDistance(data, index, red, green, blue) <= 110;
    const continuous =
      colorDistance(data, index, fromRed, fromGreen, fromBlue) <= 42 &&
      colorDistance(data, index, red, green, blue) <= 160;
    if (!backdrop && !continuous) return;
    removed[pixel] = 1;
    queue.push(pixel);
  };

  for (let x = 0; x < width; x += 1) {
    mark(x, 0, red, green, blue);
    mark(x, height - 1, red, green, blue);
  }
  for (let y = 0; y < height; y += 1) {
    mark(0, y, red, green, blue);
    mark(width - 1, y, red, green, blue);
  }

  let head = 0;
  while (head < queue.length) {
    const pixel = queue[head];
    head += 1;
    const x = pixel % width;
    const y = (pixel - x) / width;
    const index = pixel * 4;
    mark(x + 1, y, data[index], data[index + 1], data[index + 2]);
    mark(x - 1, y, data[index], data[index + 1], data[index + 2]);
    mark(x, y + 1, data[index], data[index + 1], data[index + 2]);
    mark(x, y - 1, data[index], data[index + 1], data[index + 2]);
  }

  for (let pixel = 0; pixel < removed.length; pixel += 1) {
    if (removed[pixel]) data[pixel * 4 + 3] = 0;
  }
}

export async function cutoutFile(file: File) {
  const bitmap = await createImageBitmap(file);
  try {
    const maxSide = 1024;
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const context = canvas.getContext('2d', { willReadFrequently: true });
    if (!context) throw new Error('Could not read that image.');
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const image = context.getImageData(0, 0, canvas.width, canvas.height);
    clearBackdrop(image.data, canvas.width, canvas.height);
    context.putImageData(image, 0, 0);
    return canvas.toDataURL('image/png');
  } finally {
    bitmap.close();
  }
}
