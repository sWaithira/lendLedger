/** Shrinks a photo in the browser to a JPEG of at most about 450 KB, so ID photos stay small in the database. */
export async function shrinkImage(file, maxSide = 1400, maxBytes = 450 * 1024) {
    let bitmap
    try {
        bitmap = await createImageBitmap(file)
    } catch {
        throw new Error('That photo could not be read. Use a JPEG, PNG or WebP photo.')
    }
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close?.()
    for (const quality of [0.82, 0.7, 0.58, 0.45, 0.35]) {
        const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
        if (blob && blob.size <= maxBytes) return blob
    }
    throw new Error('That photo is too detailed to store. Take it again a little further away.')
}