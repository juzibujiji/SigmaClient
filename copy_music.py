import shutil, os

dst = 'src/main/resources/bundled_music'
os.makedirs(dst, exist_ok=True)
src = 'sigma5/music'

copied = 0
for f in os.listdir(src):
    if f.endswith('.mp3') or f.endswith('.lrc') or f.endswith('.png'):
        ext = os.path.splitext(f)[1]
        # Use the original filename but also keep a mapping
        target_name = f
        src_path = os.path.join(src, f)
        dst_path = os.path.join(dst, target_name)
        shutil.copy2(src_path, dst_path)
        copied += 1
        print("Copied: " + repr(f.encode('utf-8')) + " (" + str(os.path.getsize(dst_path)) + " bytes)")

print("Total copied: " + str(copied))
print("Files in dst: " + str(os.listdir(dst)))