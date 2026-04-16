#!/usr/bin/env python3
"""
Extract table data from 53 images of Vietnamese education documents.
Images are organized by class: Lớp 10 (1-16), Lớp 11 (17-35), Lớp 12 (36-53)
Table structure: 9 columns
"""

import pytesseract
from PIL import Image, ImageEnhance, ImageFilter
import pandas as pd
import numpy as np
from openpyxl import Workbook
from openpyxl.styles import Font, Alignment, Border, Side, PatternFill
from openpyxl.utils import get_column_letter
import cv2
import os
import re
import sys

IMG_DIR = "/home/runner/work/doc/doc"

# Class boundaries (determined from OCR scan)
CLASS_RANGES = {
    "Lớp 10": (1, 16),
    "Lớp 11": (17, 35),
    "Lớp 12": (36, 53),
}

# Table column headers
HEADERS = [
    "STT",
    "Năng lực thành phần",
    "Biểu hiện năng lực thành phần",
    "Yêu cầu cần đạt",
    "Mạch nội dung",
    "Đơn vị kiến thức",
    "Các dạng câu hỏi",
    "Mức độ",
    "Hình thức câu hỏi",
]


def preprocess_image(image_path, contrast=1.8):
    """Preprocess image for better OCR"""
    img = Image.open(image_path)
    
    # Convert to grayscale
    gray = img.convert('L')
    
    # Enhance contrast
    enhancer = ImageEnhance.Contrast(gray)
    enhanced = enhancer.enhance(contrast)
    
    # Sharpen
    enhanced = enhanced.filter(ImageFilter.SHARPEN)
    
    return enhanced, img.size


def get_word_data(image_path):
    """Get word-level OCR data with positions, trying multiple preprocessing levels"""
    img = Image.open(image_path)
    w, h = img.size
    
    # Try different contrast levels
    for contrast in [1.8, 1.3, 1.0, 2.5]:
        enhanced, _ = preprocess_image(image_path, contrast=contrast)
        
        tsv = pytesseract.image_to_data(enhanced, lang='vie', output_type=pytesseract.Output.DATAFRAME)
        # Convert text to string FIRST to avoid accessor errors on float values
        tsv['text'] = tsv['text'].astype(str)
        tsv = tsv[tsv['text'].str.strip() != '']
        tsv = tsv[tsv['text'] != 'nan']
        tsv = tsv[tsv['conf'] > 15]
        
        if len(tsv) > 5:  # found enough words
            break
    
    # Also try with original RGB image if still empty
    if len(tsv) <= 5:
        tsv = pytesseract.image_to_data(img, lang='vie', output_type=pytesseract.Output.DATAFRAME)
        tsv['text'] = tsv['text'].astype(str)
        tsv = tsv[tsv['text'].str.strip() != '']
        tsv = tsv[tsv['text'] != 'nan']
        tsv = tsv[tsv['conf'] > 10]
    
    if len(tsv) == 0:
        return pd.DataFrame(), w, h
    
    tsv['cx'] = tsv['left'] + tsv['width'] / 2
    tsv['cy'] = tsv['top'] + tsv['height'] / 2
    tsv['text'] = tsv['text'].str.strip()
    
    return tsv, w, h


def detect_column_boundaries(image_path):
    """Detect vertical line positions using OpenCV to find column boundaries"""
    img = cv2.imread(image_path)
    if img is None:
        return None
    
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    h, w = gray.shape
    
    # Binary threshold
    _, binary = cv2.threshold(gray, 160, 255, cv2.THRESH_BINARY_INV)
    
    # Detect vertical lines
    v_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (2, h // 8))
    v_lines = cv2.morphologyEx(binary, cv2.MORPH_OPEN, v_kernel, iterations=2)
    
    # Get vertical line x-positions
    col_sum = np.sum(v_lines, axis=0) / 255
    # Find peaks (columns with many white pixels = vertical lines)
    threshold = h * 0.15  # At least 15% of image height
    line_positions = np.where(col_sum > threshold)[0]
    
    if len(line_positions) == 0:
        return None
    
    # Group close positions
    groups = []
    current_group = [line_positions[0]]
    for pos in line_positions[1:]:
        if pos - current_group[-1] < 30:
            current_group.append(pos)
        else:
            groups.append(int(np.mean(current_group)))
            current_group = [pos]
    groups.append(int(np.mean(current_group)))
    
    return groups


def assign_words_to_columns(tsv, col_boundaries, w):
    """Assign each word to a column based on its x-position"""
    if col_boundaries is None or len(col_boundaries) < 2:
        return None
    
    # Add edges if missing
    if col_boundaries[0] > w * 0.05:
        col_boundaries = [0] + col_boundaries
    if col_boundaries[-1] < w * 0.95:
        col_boundaries = col_boundaries + [w]
    
    def get_col_idx(cx):
        for i in range(len(col_boundaries) - 1):
            if col_boundaries[i] <= cx < col_boundaries[i + 1]:
                return i
        return len(col_boundaries) - 2
    
    tsv['col_idx'] = tsv['cx'].apply(get_col_idx)
    return tsv


def group_into_rows(tsv, threshold=45):
    """Group words into rows based on y-position"""
    if len(tsv) == 0:
        return []
    
    tsv_sorted = tsv.sort_values('top')
    
    rows = []
    current_row_y = tsv_sorted.iloc[0]['top']
    current_row_indices = [tsv_sorted.index[0]]
    
    for idx in tsv_sorted.index[1:]:
        if abs(tsv_sorted.loc[idx, 'top'] - current_row_y) < threshold:
            current_row_indices.append(idx)
        else:
            rows.append(current_row_indices)
            current_row_indices = [idx]
            current_row_y = tsv_sorted.loc[idx, 'top']
    rows.append(current_row_indices)
    
    return rows


def merge_row_text(tsv, row_indices, num_cols):
    """Merge words in a row into column texts"""
    row_data = tsv.loc[row_indices]
    
    cells = {}
    for col_idx in range(num_cols):
        col_words = row_data[row_data['col_idx'] == col_idx].sort_values('left')
        cells[col_idx] = ' '.join(str(t) for t in col_words['text'].tolist())
    
    return cells


def extract_table_data(image_path):
    """Main extraction function for a single image"""
    tsv, w, h = get_word_data(image_path)
    
    # Get raw text using multiple methods for best result
    raw_text = ""
    img = Image.open(image_path)
    for contrast in [1.8, 1.3, 1.0]:
        enhanced, _ = preprocess_image(image_path, contrast=contrast)
        text = pytesseract.image_to_string(enhanced, lang='vie', config='--psm 6')
        if len(text) > len(raw_text):
            raw_text = text
    # Also try original
    text = pytesseract.image_to_string(img, lang='vie')
    if len(text) > len(raw_text):
        raw_text = text
    
    if len(tsv) == 0:
        return [], raw_text
    
    # Ensure text column is string type
    tsv['text'] = tsv['text'].astype(str)
    
    # Detect column boundaries
    col_boundaries = detect_column_boundaries(image_path)
    
    if col_boundaries is not None and len(col_boundaries) >= 8:
        # We have enough column boundaries - use them
        tsv = assign_words_to_columns(tsv, col_boundaries, w)
        
        if tsv is not None:
            rows = group_into_rows(tsv)
            num_cols = tsv['col_idx'].max() + 1
            
            table_rows = []
            for row_indices in rows:
                cells = merge_row_text(tsv, row_indices, num_cols)
                row_list = [cells.get(i, '') for i in range(num_cols)]
                if any(str(r).strip() for r in row_list):
                    table_rows.append(row_list)
            
            return table_rows, raw_text
    
    # Fallback: use approximate column boundaries based on image width
    # These are approximate fractions for the 9-column table
    col_fracs = [0.0, 0.04, 0.10, 0.17, 0.38, 0.49, 0.62, 0.80, 0.88, 1.0]
    col_boundaries = [int(f * w) for f in col_fracs]
    
    tsv = assign_words_to_columns(tsv, col_boundaries, w)
    if tsv is None:
        return [], raw_text
    
    rows = group_into_rows(tsv)
    table_rows = []
    for row_indices in rows:
        cells = merge_row_text(tsv, row_indices, 9)
        row_list = [cells.get(i, '') for i in range(9)]
        if any(str(r).strip() for r in row_list):
            table_rows.append(row_list)
    
    return table_rows, raw_text


def process_all_images():
    """Process all 53 images and return data organized by class"""
    all_data = {}
    
    for class_name, (start, end) in CLASS_RANGES.items():
        print(f"\n{'='*60}")
        print(f"Processing {class_name} (images {start}-{end})")
        print(f"{'='*60}")
        
        class_data = []
        
        for i in range(start, end + 1):
            image_path = os.path.join(IMG_DIR, f"{i}.jpg")
            if not os.path.exists(image_path):
                print(f"  Image {i}.jpg not found, skipping")
                continue
            
            print(f"  Processing image {i}.jpg...")
            try:
                table_rows, raw_text = extract_table_data(image_path)
                class_data.append({
                    'image': i,
                    'table_rows': table_rows,
                    'raw_text': raw_text,
                })
                print(f"    -> {len(table_rows)} rows extracted, {len(raw_text)} chars raw text")
            except Exception as e:
                print(f"    ERROR: {e}")
                class_data.append({
                    'image': i,
                    'table_rows': [],
                    'raw_text': f"ERROR: {e}",
                })
        
        all_data[class_name] = class_data
    
    return all_data


def write_excel(all_data, output_path):
    """Write extracted data to Excel with 3 sheets"""
    wb = Workbook()
    
    # Styles
    header_font = Font(bold=True, size=11)
    header_fill = PatternFill(start_color="4472C4", end_color="4472C4", fill_type="solid")
    header_font_white = Font(bold=True, size=11, color="FFFFFF")
    wrap_alignment = Alignment(wrap_text=True, vertical='top')
    thin_border = Border(
        left=Side(style='thin'),
        right=Side(style='thin'),
        top=Side(style='thin'),
        bottom=Side(style='thin'),
    )
    img_marker_fill = PatternFill(start_color="E2EFDA", end_color="E2EFDA", fill_type="solid")
    img_marker_font = Font(bold=True, italic=True, size=10, color="548235")
    
    for sheet_idx, (class_name, class_data) in enumerate(all_data.items()):
        if sheet_idx == 0:
            ws = wb.active
            ws.title = class_name
        else:
            ws = wb.create_sheet(title=class_name)
        
        # Write main header
        ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=9)
        title_cell = ws.cell(row=1, column=1, value=f"Khung năng lực và đặc tả kĩ thuật - {class_name}")
        title_cell.font = Font(bold=True, size=14)
        title_cell.alignment = Alignment(horizontal='center')
        
        # Write column headers
        for col_idx, header in enumerate(HEADERS):
            cell = ws.cell(row=2, column=col_idx + 1, value=header)
            cell.font = header_font_white
            cell.fill = header_fill
            cell.alignment = Alignment(wrap_text=True, horizontal='center', vertical='center')
            cell.border = thin_border
        
        # Set column widths
        col_widths = [6, 15, 15, 35, 18, 22, 30, 10, 12]
        for i, width in enumerate(col_widths):
            ws.column_dimensions[get_column_letter(i + 1)].width = width
        
        current_row = 3
        
        for img_data in class_data:
            img_num = img_data['image']
            table_rows = img_data['table_rows']
            raw_text = img_data['raw_text']
            
            # Write image marker row
            ws.merge_cells(start_row=current_row, start_column=1, end_row=current_row, end_column=9)
            marker = ws.cell(row=current_row, column=1, value=f"--- Hình {img_num}.jpg ---")
            marker.font = img_marker_font
            marker.fill = img_marker_fill
            marker.alignment = Alignment(horizontal='center')
            current_row += 1
            
            if table_rows:
                for row_data in table_rows:
                    # Ensure we have exactly 9 columns
                    while len(row_data) < 9:
                        row_data.append('')
                    
                    for col_idx in range(9):
                        cell_value = row_data[col_idx] if col_idx < len(row_data) else ''
                        cell = ws.cell(row=current_row, column=col_idx + 1, value=cell_value)
                        cell.alignment = wrap_alignment
                        cell.border = thin_border
                    current_row += 1
            else:
                # If no table rows extracted, put raw text in a merged cell
                ws.merge_cells(start_row=current_row, start_column=1, end_row=current_row, end_column=9)
                cell = ws.cell(row=current_row, column=1, value=raw_text[:5000] if raw_text else "(Không trích xuất được)")
                cell.alignment = wrap_alignment
                current_row += 1
            
            current_row += 1  # Empty row between images
        
        # Freeze header row
        ws.freeze_panes = 'A3'
    
    # Also create a "Raw Text" sheet with all raw OCR text for reference
    ws_raw = wb.create_sheet(title="Raw Text (Tham khảo)")
    ws_raw.cell(row=1, column=1, value="Hình").font = header_font
    ws_raw.cell(row=1, column=2, value="Lớp").font = header_font
    ws_raw.cell(row=1, column=3, value="Nội dung OCR").font = header_font
    ws_raw.column_dimensions['A'].width = 8
    ws_raw.column_dimensions['B'].width = 10
    ws_raw.column_dimensions['C'].width = 120
    
    raw_row = 2
    for class_name, class_data in all_data.items():
        for img_data in class_data:
            ws_raw.cell(row=raw_row, column=1, value=img_data['image'])
            ws_raw.cell(row=raw_row, column=2, value=class_name)
            ws_raw.cell(row=raw_row, column=3, value=img_data['raw_text'][:32000])
            ws_raw.cell(row=raw_row, column=3).alignment = wrap_alignment
            raw_row += 1
    
    wb.save(output_path)
    print(f"\n{'='*60}")
    print(f"Excel file saved to: {output_path}")
    print(f"{'='*60}")


if __name__ == "__main__":
    output_path = os.path.join(IMG_DIR, "extracted_data.xlsx")
    
    print("Starting OCR extraction from 53 images...")
    print(f"Output: {output_path}")
    
    all_data = process_all_images()
    write_excel(all_data, output_path)
    
    # Print summary
    for class_name, class_data in all_data.items():
        total_rows = sum(len(d['table_rows']) for d in class_data)
        total_images = len(class_data)
        print(f"{class_name}: {total_images} images, {total_rows} table rows extracted")
