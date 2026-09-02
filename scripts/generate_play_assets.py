import os
import math
from PIL import Image, ImageDraw, ImageFont

os.makedirs("play/assets", exist_ok=True)
os.makedirs("play/screenshots/phone", exist_ok=True)

# -------------------------------------------------------------
# 1. Store Icon (512x512 32-bit PNG)
# -------------------------------------------------------------
def render_store_icon():
    size = (512, 512)
    img = Image.new("RGBA", size, (37, 99, 235, 255)) # #2563EB
    draw = ImageDraw.Draw(img)

    # Document body (White)
    doc_poly = [(158, 112), (288, 112), (354, 178), (354, 400), (158, 400)]
    draw.polygon(doc_poly, fill=(255, 255, 255, 255))

    # Folded corner (#BFDBFE)
    fold_poly = [(288, 112), (288, 178), (354, 178)]
    draw.polygon(fold_poly, fill=(191, 219, 254, 255))

    # Document text lines (#2563EB)
    draw.rounded_rectangle([198, 224, 310, 242], radius=9, fill=(37, 99, 235, 255))
    draw.rounded_rectangle([198, 270, 310, 288], radius=9, fill=(37, 99, 235, 255))
    draw.rounded_rectangle([198, 316, 274, 334], radius=9, fill=(37, 99, 235, 255))

    # Cyan scanner corner brackets (#67E8F9), width=22
    cyan = (103, 232, 249, 255)
    w = 22

    # Top-left
    draw.line([(122, 180), (122, 130)], fill=cyan, width=w)
    draw.line([(122, 130), (172, 130)], fill=cyan, width=w)
    draw.ellipse([122 - w//2, 180 - w//2, 122 + w//2, 180 + w//2], fill=cyan)
    draw.ellipse([172 - w//2, 130 - w//2, 172 + w//2, 130 + w//2], fill=cyan)

    # Top-right
    draw.line([(340, 130), (390, 130)], fill=cyan, width=w)
    draw.line([(390, 130), (390, 180)], fill=cyan, width=w)
    draw.ellipse([340 - w//2, 130 - w//2, 340 + w//2, 130 + w//2], fill=cyan)
    draw.ellipse([390 - w//2, 180 - w//2, 390 + w//2, 180 + w//2], fill=cyan)

    # Bottom-left
    draw.line([(122, 332), (122, 382)], fill=cyan, width=w)
    draw.line([(122, 382), (172, 382)], fill=cyan, width=w)
    draw.ellipse([122 - w//2, 332 - w//2, 122 + w//2, 332 + w//2], fill=cyan)
    draw.ellipse([172 - w//2, 382 - w//2, 172 + w//2, 382 + w//2], fill=cyan)

    # Bottom-right
    draw.line([(340, 382), (390, 382)], fill=cyan, width=w)
    draw.line([(390, 382), (390, 332)], fill=cyan, width=w)
    draw.ellipse([340 - w//2, 382 - w//2, 340 + w//2, 382 + w//2], fill=cyan)
    draw.ellipse([390 - w//2, 332 - w//2, 390 + w//2, 332 + w//2], fill=cyan)

    out_path = "play/assets/docuscan-store-icon-512.png"
    img.save(out_path, format="PNG")
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")

# -------------------------------------------------------------
# 2. Feature Graphic (1024x500 RGB PNG, no alpha)
# -------------------------------------------------------------
def render_feature_graphic():
    w, h = 1024, 500
    img = Image.new("RGB", (w, h))
    draw = ImageDraw.Draw(img)

    # Linear gradient: #1D4ED8 (29, 78, 216) -> #0F766E (15, 118, 110)
    c1 = (29, 78, 216)
    c2 = (15, 118, 110)
    for y in range(h):
        for x in range(w):
            factor = (x / w + y / h) / 2.0
            r = int(c1[0] + factor * (c2[0] - c1[0]))
            g = int(c1[1] + factor * (c2[1] - c1[1]))
            b = int(c1[2] + factor * (c2[2] - c1[2]))
            draw.point((x, y), fill=(r, g, b))

    # Background ambient circles
    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    odraw = ImageDraw.Draw(overlay)
    odraw.ellipse([120-120, 80-120, 120+120, 80+120], fill=(255, 255, 255, 13))
    odraw.ellipse([940-180, 440-180, 940+180, 440+180], fill=(255, 255, 255, 13))

    # Back document (rotated card)
    back_doc = Image.new("RGBA", (310, 330), (0, 0, 0, 0))
    bdraw = ImageDraw.Draw(back_doc)
    bdraw.rounded_rectangle([0, 0, 310, 330], radius=28, fill=(191, 219, 254, 185))
    rotated_back = back_doc.rotate(-8, expand=True, resample=Image.BICUBIC)
    overlay.paste(rotated_back, (260, 60), rotated_back)

    # Main White Document Card with Shadow
    odraw.rounded_rectangle([360, 64, 670, 414], radius=30, fill=(255, 255, 255, 255))
    # Fold
    odraw.polygon([(580, 64), (670, 64), (670, 154)], fill=(219, 234, 254, 255))
    # Text bars
    odraw.rounded_rectangle([422, 192, 602, 212], radius=10, fill=(37, 99, 235, 255))
    odraw.rounded_rectangle([422, 241, 602, 261], radius=10, fill=(96, 165, 250, 255))
    odraw.rounded_rectangle([422, 290, 547, 310], radius=10, fill=(147, 197, 253, 255))

    # Scanner brackets (#67E8F9)
    cyan = (103, 232, 249, 255)
    sw = 16
    odraw.line([(320, 155), (320, 91)], fill=cyan, width=sw)
    odraw.line([(320, 91), (384, 91)], fill=cyan, width=sw)
    odraw.line([(646, 91), (710, 91)], fill=cyan, width=sw)
    odraw.line([(710, 91), (710, 155)], fill=cyan, width=sw)
    odraw.line([(320, 330), (320, 394)], fill=cyan, width=sw)
    odraw.line([(320, 394), (384, 394)], fill=cyan, width=sw)
    odraw.line([(646, 394), (710, 394)], fill=cyan, width=sw)
    odraw.line([(710, 394), (710, 330)], fill=cyan, width=sw)

    # Search Magnifying Glass
    odraw.ellipse([760, 175, 852, 267], outline=(255, 255, 255, 255), width=16)
    odraw.line([(841, 256), (889, 304)], fill=(255, 255, 255, 255), width=18)

    # Study Badge Motif (Left)
    odraw.rounded_rectangle([120, 308, 290, 380], radius=28, fill=(255, 255, 255, 230))
    odraw.ellipse([146, 326, 182, 362], fill=(37, 99, 235, 255))
    odraw.rounded_rectangle([196, 332, 258, 344], radius=6, fill=(96, 165, 250, 255))
    odraw.rounded_rectangle([196, 352, 238, 362], radius=5, fill=(147, 197, 253, 255))

    img.paste(overlay, (0, 0), overlay)

    out_path = "play/assets/docuscan-feature-graphic-1024x500.png"
    img.save(out_path, format="PNG")
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")

# -------------------------------------------------------------
# 3. Phone Screenshots (1080x1920 24-bit PNGs)
# -------------------------------------------------------------
def create_phone_screenshot(title, subtitle, draw_content_fn, filename):
    w, h = 1080, 1920
    img = Image.new("RGB", (w, h), (248, 250, 252)) # Light slate #F8FAFC
    draw = ImageDraw.Draw(img)

    # Top App Header (Brand Gradient)
    # Header card
    draw.rectangle([0, 0, w, 320], fill=(37, 99, 235)) # #2563EB

    # Top Status & Title text
    try:
        font_title = ImageFont.truetype("arial.ttf", 52)
        font_sub = ImageFont.truetype("arial.ttf", 32)
        font_card_title = ImageFont.truetype("arial.ttf", 36)
        font_card_body = ImageFont.truetype("arial.ttf", 26)
    except:
        font_title = ImageFont.load_default()
        font_sub = ImageFont.load_default()
        font_card_title = ImageFont.load_default()
        font_card_body = ImageFont.load_default()

    draw.text((64, 110), title, fill=(255, 255, 255), font=font_title)
    draw.text((64, 185), subtitle, fill=(191, 219, 254), font=font_sub)

    # Content Container
    draw_content_fn(draw, w, h, font_card_title, font_card_body)

    out_path = os.path.join("play/screenshots/phone", filename)
    img.save(out_path, format="PNG")
    print(f"Saved Screenshot: {out_path} ({os.path.getsize(out_path)} bytes)")

def draw_home(draw, w, h, f_title, f_body):
    # Search bar
    draw.rounded_rectangle([64, 360, w - 64, 450], radius=24, fill=(255, 255, 255), outline=(226, 232, 240), width=2)
    draw.text((110, 388), "Search documents, subjects & OCR text...", fill=(148, 163, 184), font=f_body)

    # Study Subjects Chips
    chips = ["All (12)", "DBMS (4)", "DSA (5)", "Mathematics (3)"]
    cx = 64
    for i, chip in enumerate(chips):
        cw = 180 if i == 0 else 230
        bg = (37, 99, 235) if i == 0 else (255, 255, 255)
        fg = (255, 255, 255) if i == 0 else (71, 85, 105)
        draw.rounded_rectangle([cx, 480, cx + cw, 550], radius=20, fill=bg, outline=(203, 213, 225), width=1)
        draw.text((cx + 28, 502), chip, fill=fg, font=f_body)
        cx += cw + 20

    # Recent Documents List
    docs = [
        ("DBMS_Normalization_Notes.pdf", "Subject: DBMS • 6 Pages • Today, 10:30 AM", (59, 130, 246)),
        ("DSA_Trees_Lecture.pdf", "Subject: DSA • 14 Pages • Yesterday", (16, 185, 129)),
        ("Mathematics_Probability_Assignment.pdf", "Subject: Mathematics • 4 Pages • Aug 30", (245, 158, 11)),
        ("Physics_Quantum_Overview.pdf", "General • 8 Pages • Aug 28", (139, 92, 246)),
        ("Operating_Systems_Process_Scheduling.pdf", "General • 12 Pages • Aug 25", (236, 72, 153))
    ]

    y = 600
    for title, meta, color in docs:
        draw.rounded_rectangle([64, y, w - 64, y + 160], radius=24, fill=(255, 255, 255), outline=(241, 245, 249), width=2)
        # Doc Icon
        draw.rounded_rectangle([96, y + 24, 196, y + 136], radius=16, fill=color)
        draw.text((120, y + 60), "PDF", fill=(255, 255, 255), font=f_body)
        # Texts
        draw.text((224, y + 36), title, fill=(15, 23, 42), font=f_title)
        draw.text((224, y + 92), meta, fill=(100, 116, 139), font=f_body)
        y += 184

    # Bottom FAB
    draw.rounded_rectangle([w - 220, h - 220, w - 80, h - 80], radius=40, fill=(37, 99, 235))
    draw.text((w - 170, h - 170), "+", fill=(255, 255, 255), font=f_title)

def draw_editor(draw, w, h, f_title, f_body):
    # Preview Area
    draw.rounded_rectangle([64, 360, w - 64, 1380], radius=32, fill=(255, 255, 255), outline=(203, 213, 225), width=2)
    # Mock document content
    draw.rounded_rectangle([128, 430, w - 128, 1300], radius=20, fill=(241, 245, 249))
    draw.text((180, 500), "Lecture Notes: B-Tree & AVL Trees", fill=(15, 23, 42), font=f_title)
    draw.line([(180, 560), (w - 180, 560)], fill=(203, 213, 225), width=4)

    for ly in range(610, 1200, 55):
        draw.line([(180, ly), (w - 180, ly)], fill=(226, 232, 240), width=3)

    # Crop boundary handles (Cyan)
    cyan = (6, 182, 212)
    draw.rectangle([128, 430, w - 128, 1300], outline=cyan, width=4)
    for px, py in [(128, 430), (w-128, 430), (128, 1300), (w-128, 1300)]:
        draw.ellipse([px-20, py-20, px+20, py+20], fill=cyan)

    # Bottom Action Bar
    draw.rounded_rectangle([64, 1430, w - 64, 1580], radius=28, fill=(255, 255, 255), outline=(226, 232, 240), width=2)
    tools = ["Crop", "Filters", "Rotate", "Retake", "Save"]
    bx = 96
    for t in tools:
        draw.text((bx + 20, 1490), t, fill=(37, 99, 235), font=f_body)
        bx += 180

def draw_study_mode(draw, w, h, f_title, f_body):
    # Study Mode Header card
    draw.rounded_rectangle([64, 360, w - 64, 520], radius=24, fill=(238, 242, 255))
    draw.text((96, 400), "Active Preset: Lecture Notes", fill=(67, 56, 202), font=f_title)
    draw.text((96, 455), "Automatic high-contrast document clean-up enabled", fill=(99, 102, 241), font=f_body)

    # Presets Grid
    presets = [("Notes", (59, 130, 246)), ("Assignments", (16, 185, 129)), ("Lectures", (245, 158, 11)), ("Whiteboard", (139, 92, 246))]
    px = 64
    for name, col in presets:
        draw.rounded_rectangle([px, 560, px + 215, 700], radius=24, fill=(255, 255, 255), outline=(226, 232, 240), width=2)
        draw.ellipse([px + 30, 590, px + 80, 640], fill=col)
        draw.text((px + 24, 650), name, fill=(15, 23, 42), font=f_body)
        px += 240

    # Subject Folders
    draw.text((64, 750), "Subject Folders", fill=(15, 23, 42), font=f_title)
    folders = [
        ("DBMS", "Database Management Systems • 4 Documents", (37, 99, 235)),
        ("DSA", "Data Structures & Algorithms • 5 Documents", (16, 185, 129)),
        ("Mathematics", "Calculus & Linear Algebra • 3 Documents", (245, 158, 11)),
        ("Physics", "Quantum & Mechanics • 2 Documents", (139, 92, 246))
    ]
    fy = 820
    for name, desc, col in folders:
        draw.rounded_rectangle([64, fy, w - 64, fy + 160], radius=24, fill=(255, 255, 255), outline=(226, 232, 240), width=2)
        draw.rounded_rectangle([96, fy + 30, 196, fy + 130], radius=20, fill=col)
        draw.text((125, fy + 60), "📁", fill=(255, 255, 255), font=f_body)
        draw.text((224, fy + 40), name, fill=(15, 23, 42), font=f_title)
        draw.text((224, fy + 95), desc, fill=(100, 116, 139), font=f_body)
        fy += 190

def draw_ocr_search(draw, w, h, f_title, f_body):
    # Active Search Bar
    draw.rounded_rectangle([64, 360, w - 64, 460], radius=24, fill=(255, 255, 255), outline=(37, 99, 235), width=3)
    draw.text((110, 395), "Binary Search Tree", fill=(15, 23, 42), font=f_title)

    draw.text((64, 500), "3 Occurrences across 2 documents", fill=(100, 116, 139), font=f_body)

    # Search Results
    results = [
        ("DSA_Trees_Lecture.pdf", "Page 4", "…insertion into a balanced Binary Search Tree guarantees O(log N) time…"),
        ("DSA_Trees_Lecture.pdf", "Page 9", "…deletion operations on Binary Search Tree nodes with two children…"),
        ("DSA_Midterm_Prep.pdf", "Page 2", "…explain the worst-case scenario for un-balanced Binary Search Tree…")
    ]

    ry = 560
    for doc, page, snippet in results:
        draw.rounded_rectangle([64, ry, w - 64, ry + 240], radius=24, fill=(255, 255, 255), outline=(226, 232, 240), width=2)
        draw.text((96, ry + 30), doc, fill=(37, 99, 235), font=f_title)
        draw.rounded_rectangle([w - 240, ry + 30, w - 96, ry + 80], radius=14, fill=(239, 246, 255))
        draw.text((w - 210, ry + 42), page, fill=(37, 99, 235), font=f_body)
        draw.text((96, ry + 105), snippet[:48], fill=(51, 65, 85), font=f_body)
        draw.text((96, ry + 150), snippet[48:], fill=(51, 65, 85), font=f_body)
        ry += 270

def draw_pdf_tools(draw, w, h, f_title, f_body):
    tools = [
        ("Merge PDFs", "Combine multiple PDF files into one clean document", (37, 99, 235)),
        ("Split PDF", "Extract custom page ranges or split into single pages", (16, 185, 129)),
        ("Rotate Pages", "Fix orientation of individual or all pages", (245, 158, 11)),
        ("Reorder & Delete", "Rearrange page sequence and remove unwanted sheets", (139, 92, 246)),
        ("Watermark PDF", "Add custom text watermarks for assignments", (6, 182, 212)),
        ("Password Protect", "Secure documents with standard PDF encryption", (236, 72, 153))
    ]

    ty = 360
    for name, desc, col in tools:
        draw.rounded_rectangle([64, ty, w - 64, ty + 160], radius=24, fill=(255, 255, 255), outline=(226, 232, 240), width=2)
        draw.rounded_rectangle([96, ty + 30, 196, ty + 130], radius=20, fill=col)
        draw.text((224, ty + 40), name, fill=(15, 23, 42), font=f_title)
        draw.text((224, ty + 95), desc, fill=(100, 116, 139), font=f_body)
        ty += 184

def draw_privacy(draw, w, h, f_title, f_body):
    # Trust banner
    draw.rounded_rectangle([64, 360, w - 64, 520], radius=24, fill=(219, 234, 254))
    draw.text((96, 400), "Local by default", fill=(30, 64, 175), font=f_title)
    draw.text((96, 455), "Core scanning, OCR and PDF tools run on-device", fill=(30, 64, 175), font=f_body)

    facts = [
        ("Camera Permission", "Permission granted (Optional, on-demand scan only)"),
        ("Internet Permission", "Not requested (Zero network tracking)"),
        ("Android App Backup", "Disabled (Excluded from cloud backup)"),
        ("App-Managed Documents", "12 Documents (28.4 MB in private storage)"),
        ("OCR Search Text", "Indexed locally in Room Database"),
        ("Erase Document Data", "One-tap complete wipe of all local data")
    ]

    fy = 560
    for title, val in facts:
        draw.rounded_rectangle([64, fy, w - 64, fy + 140], radius=20, fill=(255, 255, 255), outline=(226, 232, 240), width=2)
        draw.text((96, fy + 30), title, fill=(15, 23, 42), font=f_title)
        draw.text((96, fy + 80), val, fill=(100, 116, 139), font=f_body)
        fy += 164

if __name__ == "__main__":
    render_store_icon()
    render_feature_graphic()

    create_phone_screenshot("DocuScan", "Scan, search and organize your study documents", draw_home, "1_home.png")
    create_phone_screenshot("Page Editor", "Clean pages, crop edges and adjust filters", draw_editor, "2_editor.png")
    create_phone_screenshot("Study Mode", "Organize scans by subject folders and study presets", draw_study_mode, "3_study_mode.png")
    create_phone_screenshot("OCR Search", "Search text inside PDFs and jump to exact pages", draw_ocr_search, "4_ocr_search.png")
    create_phone_screenshot("PDF Tools", "Merge, split, reorder, rotate and manage PDFs", draw_pdf_tools, "5_pdf_tools.png")
    create_phone_screenshot("Privacy & Data", "See what stays local with full data erasure controls", draw_privacy, "6_privacy.png")
