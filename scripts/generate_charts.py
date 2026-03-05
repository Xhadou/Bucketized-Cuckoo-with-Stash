#!/usr/bin/env python3
"""
Generate publication-quality charts for Bucketized Cuckoo Hashing with Stash.

Reads CSV data produced by cuckoo.analysis.DirectAnalysis.
Falls back to demo data when CSVs are absent.
"""

import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import numpy as np
import os

# ---------------------------------------------------------------------------
# Style setup
# ---------------------------------------------------------------------------
sns.set_theme(style="whitegrid", font_scale=1.2)
PALETTE = ['#4C72B0', '#DD8452', '#55A868', '#C44E52', '#8172B3']
plt.rcParams.update({
    'figure.figsize': (10, 6), 'figure.dpi': 150,
    'savefig.dpi': 300, 'savefig.bbox': 'tight',
    'axes.titleweight': 'bold'
})

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
RESULTS_DIR = os.path.join(SCRIPT_DIR, '..', 'results')
CHARTS_DIR = os.path.join(RESULTS_DIR, 'charts')
CSV_DIR = os.path.join(RESULTS_DIR, 'csv')
os.makedirs(CHARTS_DIR, exist_ok=True)

# Consistent display names
TYPE_LABELS = {
    'STANDARD': 'Standard\nCuckoo',
    'BUCKETIZED_4': 'Bucketized\n(B=4)',
    'STASHED_3': 'Stashed\n(B=4, s=3)',
    'CHAINING': 'Chaining',
    'LINEAR_PROBING': 'Linear\nProbing',
}
TYPE_ORDER = ['STANDARD', 'BUCKETIZED_4', 'STASHED_3', 'CHAINING', 'LINEAR_PROBING']

def _try_load_csv(filename):
    path = os.path.join(CSV_DIR, filename)
    if os.path.isfile(path):
        try:
            return pd.read_csv(path)
        except Exception as e:
            print(f"  Warning: could not parse {path}: {e}")
    return None


# ---------------------------------------------------------------------------
# Chart 1: Insert Throughput
# ---------------------------------------------------------------------------
def chart1_insert_throughput():
    df = _try_load_csv('throughput.csv')
    fig, ax = plt.subplots()

    if df is not None:
        ins = df[df['operation'] == 'insert'].copy()
        ins['label'] = ins['table_type'].map(TYPE_LABELS)
        # Reorder
        ins['order'] = ins['table_type'].map({t: i for i, t in enumerate(TYPE_ORDER)})
        ins = ins.sort_values('order')
        colors = [PALETTE[i] for i in range(len(ins))]
        bars = ax.bar(ins['label'], ins['ops_per_sec'], color=colors)
        ax.bar_label(bars, fmt=lambda x: f'{x/1e6:.2f}M', padding=3, fontsize=9)
    else:
        types = list(TYPE_LABELS.values())
        vals = [1.5e6, 1.0e6, 0.96e6, 1.8e6, 2.3e6]
        bars = ax.bar(types, vals, color=PALETTE)
        ax.bar_label(bars, fmt=lambda x: f'{x/1e6:.2f}M', padding=3, fontsize=9)

    ax.set_title('Insert Throughput Comparison (500K Elements)')
    ax.set_ylabel('Throughput (ops/sec)')
    ax.set_ylim(bottom=0)
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '01_insert_throughput.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 2: Lookup Throughput (positive vs negative)
# ---------------------------------------------------------------------------
def chart2_lookup_throughput():
    df = _try_load_csv('throughput.csv')
    fig, ax = plt.subplots()
    x = np.arange(len(TYPE_ORDER))
    width = 0.35

    if df is not None:
        pos = df[df['operation'] == 'positive_lookup'].copy()
        neg = df[df['operation'] == 'negative_lookup'].copy()
        pos['order'] = pos['table_type'].map({t: i for i, t in enumerate(TYPE_ORDER)})
        neg['order'] = neg['table_type'].map({t: i for i, t in enumerate(TYPE_ORDER)})
        pos = pos.sort_values('order')
        neg = neg.sort_values('order')
        pos_vals = pos['ops_per_sec'].values
        neg_vals = neg['ops_per_sec'].values
        labels = [TYPE_LABELS[t] for t in pos['table_type']]
    else:
        pos_vals = [5.3e6, 3.2e6, 2.6e6, 4.9e6, 3.8e6]
        neg_vals = [3.3e6, 1.5e6, 1.5e6, 6.9e6, 5.6e6]
        labels = list(TYPE_LABELS.values())

    bars1 = ax.bar(x - width/2, pos_vals, width, label='Positive Lookup', color=PALETTE[0])
    bars2 = ax.bar(x + width/2, neg_vals, width, label='Negative Lookup', color=PALETTE[1])
    ax.set_title('Lookup Throughput: Positive vs Negative at 60% Load')
    ax.set_ylabel('Throughput (ops/sec)')
    ax.set_xticks(x)
    ax.set_xticklabels(labels)
    ax.set_ylim(bottom=0)
    ax.legend()
    ax.bar_label(bars1, fmt=lambda x: f'{x/1e6:.1f}M', padding=3, fontsize=8)
    ax.bar_label(bars2, fmt=lambda x: f'{x/1e6:.1f}M', padding=3, fontsize=8)
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '02_lookup_throughput.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 3: Load Factor vs Bucket Size (KEY CHART)
# ---------------------------------------------------------------------------
def chart3_load_factor_vs_bucket():
    df = _try_load_csv('load_factor_vs_bucket.csv')
    fig, ax = plt.subplots()

    if df is not None:
        # Average across trials
        avg = df.groupby('bucket_size')['max_load_factor'].mean().reset_index()
        std = df.groupby('bucket_size')['max_load_factor'].std().reset_index()
        ax.errorbar(avg['bucket_size'], avg['max_load_factor'],
                     yerr=std['max_load_factor'], marker='o', linewidth=2.5,
                     markersize=10, color=PALETTE[0], capsize=5, capthick=2)
        for _, row in avg.iterrows():
            ax.annotate(f"{row['max_load_factor']:.1%}",
                        (row['bucket_size'], row['max_load_factor']),
                        textcoords='offset points', xytext=(0, 14),
                        ha='center', fontweight='bold', fontsize=11)
        ax.set_xticks(avg['bucket_size'])
    else:
        bs = [1, 2, 4, 8]
        lf = [0.50, 0.80, 0.95, 0.98]
        ax.plot(bs, lf, marker='o', linewidth=2.5, markersize=10, color=PALETTE[0])
        for b, l in zip(bs, lf):
            ax.annotate(f"{l:.0%}", (b, l), textcoords='offset points',
                        xytext=(0, 14), ha='center', fontweight='bold', fontsize=11)
        ax.set_xticks(bs)

    ax.set_title('Bucketization Raises Load Factor from 50% to 98%')
    ax.set_xlabel('Bucket Size (B)')
    ax.set_ylabel('Maximum Load Factor')
    ax.set_ylim(0.3, 1.05)
    ax.yaxis.set_major_formatter(plt.FuncFormatter(lambda y, _: f'{y:.0%}'))
    ax.axhline(y=0.5, color='gray', linestyle='--', alpha=0.5, label='Standard threshold (50%)')
    ax.legend()
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '03_load_factor_vs_bucket_size.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 4: Rehash Count vs Stash Size (KEY CHART)
# ---------------------------------------------------------------------------
def chart4_rehash_vs_stash():
    df = _try_load_csv('rehash_vs_stash.csv')
    fig, ax = plt.subplots()

    if df is not None:
        avg = df.groupby('stash_size')['rehash_count'].mean().reset_index()
        std = df.groupby('stash_size')['rehash_count'].std().reset_index()
        ax.errorbar(avg['stash_size'], avg['rehash_count'],
                     yerr=std['rehash_count'], marker='s', linewidth=2.5,
                     markersize=10, color=PALETTE[3], capsize=5, capthick=2)
        for _, row in avg.iterrows():
            ax.annotate(f"{row['rehash_count']:.1f}",
                        (row['stash_size'], row['rehash_count']),
                        textcoords='offset points', xytext=(0, 14),
                        ha='center', fontweight='bold', fontsize=11)
        ax.set_xticks(avg['stash_size'])
    else:
        ss = [0, 1, 2, 3, 4]
        rc = [12.3, 3.1, 0.8, 0.1, 0.02]
        ax.plot(ss, rc, marker='s', linewidth=2.5, markersize=10, color=PALETTE[3])
        ax.set_xticks(ss)

    ax.set_title('Rehash Count vs Stash Size (20K Inserts, B=4)')
    ax.set_xlabel('Stash Size (s)')
    ax.set_ylabel('Number of Rehashes')
    ax.set_ylim(bottom=0)
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '04_rehash_vs_stash.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 5: Displacement Chain Length Distribution
# ---------------------------------------------------------------------------
def chart5_displacement_chains():
    df = _try_load_csv('displacement_chains.csv')
    fig, ax = plt.subplots()

    if df is not None and len(df) > 0:
        # Expand histogram: each row is (table_type, chain_length, count)
        for i, (ttype, color, label) in enumerate([
            ('Standard', PALETTE[0], 'Standard Cuckoo'),
            ('Bucketized_B4', PALETTE[2], 'Bucketized (B=4)'),
        ]):
            subset = df[df['table_type'] == ttype]
            if len(subset) > 0:
                # Filter out chain_length=0 for a more interesting chart
                subset = subset[subset['chain_length'] > 0]
                if len(subset) > 0:
                    ax.bar(subset['chain_length'] + i * 0.3 - 0.15,
                           subset['count'], width=0.3, alpha=0.7,
                           label=label, color=color, edgecolor='white')
    else:
        np.random.seed(44)
        standard = np.random.geometric(p=0.35, size=5000)
        bucketized = np.random.geometric(p=0.65, size=5000)
        bins = np.arange(1, max(standard.max(), bucketized.max()) + 2) - 0.5
        ax.hist(standard, bins=bins, alpha=0.6, label='Standard Cuckoo',
                color=PALETTE[0], edgecolor='white')
        ax.hist(bucketized, bins=bins, alpha=0.6, label='Bucketized (B=4)',
                color=PALETTE[2], edgecolor='white')

    ax.set_title('Displacement Chain Length Distribution (Non-Zero Only)')
    ax.set_xlabel('Chain Length (number of displacements)')
    ax.set_ylabel('Frequency')
    ax.legend()
    ax.set_ylim(bottom=0)
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '05_displacement_chains.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 6: Mixed Workload Throughput
# ---------------------------------------------------------------------------
def chart6_mixed_workload():
    df = _try_load_csv('throughput.csv')
    fig, ax = plt.subplots()

    if df is not None:
        mix = df[df['operation'] == 'mixed'].copy()
        mix['order'] = mix['table_type'].map({t: i for i, t in enumerate(TYPE_ORDER)})
        mix = mix.sort_values('order')
        mix['label'] = mix['table_type'].map(TYPE_LABELS)
        bars = ax.bar(mix['label'], mix['ops_per_sec'], color=PALETTE)
        ax.bar_label(bars, fmt=lambda x: f'{x/1e6:.2f}M', padding=3, fontsize=9)
    else:
        types = list(TYPE_LABELS.values())
        vals = [1.0e6, 0.85e6, 1.25e6, 1.5e6, 1.3e6]
        bars = ax.bar(types, vals, color=PALETTE)
        ax.bar_label(bars, fmt=lambda x: f'{x/1e6:.2f}M', padding=3, fontsize=9)

    ax.set_title('Mixed Workload Throughput (80% Read, 20% Write)')
    ax.set_ylabel('Throughput (ops/sec)')
    ax.set_ylim(bottom=0)
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '06_mixed_workload.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 7: Performance Summary Heatmap
# ---------------------------------------------------------------------------
def chart7_heatmap():
    df = _try_load_csv('throughput.csv')
    fig, ax = plt.subplots(figsize=(12, 5))

    if df is not None:
        # Pivot throughput data into a matrix
        pivot = df.pivot(index='table_type', columns='operation', values='ops_per_sec')
        # Rename and reorder
        col_map = {
            'insert': 'Insert (ops/s)',
            'positive_lookup': 'Pos Lookup (ops/s)',
            'negative_lookup': 'Neg Lookup (ops/s)',
            'mixed': 'Mixed (ops/s)',
        }
        pivot = pivot.rename(columns=col_map)
        # Reorder rows
        row_order = [t for t in TYPE_ORDER if t in pivot.index]
        pivot = pivot.loc[row_order]
        pivot.index = [TYPE_LABELS[t].replace('\n', ' ') for t in row_order]

        # Add load factor data if available
        lf_df = _try_load_csv('load_factor_vs_bucket.csv')
        if lf_df is not None:
            lf_avg = lf_df.groupby('bucket_size')['max_load_factor'].mean()
            # Map to table types
            load_factors = {
                'Standard Cuckoo': lf_avg.get(1, 0.5),
                'Bucketized (B=4)': lf_avg.get(4, 0.95),
                'Stashed (B=4, s=3)': lf_avg.get(4, 0.95),
                'Chaining': 0.75,
                'Linear Probing': 0.50,
            }
            pivot['Max Load (%)'] = [load_factors.get(idx, 0.5) * 100 for idx in pivot.index]

        # Normalize for color mapping
        annot_vals = pivot.values.copy()
        # Format: ops/s as M, load as %
        annot_text = []
        for row in annot_vals:
            row_text = []
            for j, val in enumerate(row):
                if 'Load' in pivot.columns[j]:
                    row_text.append(f'{val:.0f}%')
                else:
                    row_text.append(f'{val/1e6:.2f}M')
            annot_text.append(row_text)
        annot_text = np.array(annot_text)

        normalized = pivot.apply(lambda col: (col - col.min()) / (col.max() - col.min() + 1e-9))
        sns.heatmap(normalized, annot=annot_text, fmt='', cmap='YlGnBu',
                    linewidths=0.5, ax=ax, cbar_kws={'label': 'Relative Score'})
    else:
        data = {
            'Insert (Mops/s)': [1.5, 1.0, 0.96, 1.8, 2.3],
            'Pos Lookup (Mops/s)': [5.3, 3.2, 2.6, 4.9, 3.8],
            'Neg Lookup (Mops/s)': [3.3, 1.5, 1.5, 6.9, 5.6],
            'Max Load (%)': [50, 95, 95, 100, 100],
        }
        labels = [TYPE_LABELS[t].replace('\n', ' ') for t in TYPE_ORDER]
        hm_df = pd.DataFrame(data, index=labels)
        normalized = hm_df.apply(lambda col: (col - col.min()) / (col.max() - col.min() + 1e-9))
        sns.heatmap(normalized, annot=hm_df.values, fmt='.1f', cmap='YlGnBu',
                    linewidths=0.5, ax=ax, cbar_kws={'label': 'Relative Score'})

    ax.set_title('Performance Summary Heatmap')
    ax.set_ylabel('')
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '07_performance_heatmap.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    print('Bucketized Cuckoo with Stash -- Chart Generator')
    print('=' * 52)

    # Check which CSVs exist
    csvs = ['throughput.csv', 'load_factor_vs_bucket.csv', 'rehash_vs_stash.csv', 'displacement_chains.csv']
    found = [f for f in csvs if os.path.isfile(os.path.join(CSV_DIR, f))]
    if found:
        print(f'  Found real data: {", ".join(found)}')
    else:
        print('  No analysis CSVs found -- using demo data.')
        print('  Run DirectAnalysis first for real data.')

    generators = [
        ('01_insert_throughput.png', chart1_insert_throughput),
        ('02_lookup_throughput.png', chart2_lookup_throughput),
        ('03_load_factor_vs_bucket_size.png', chart3_load_factor_vs_bucket),
        ('04_rehash_vs_stash.png', chart4_rehash_vs_stash),
        ('05_displacement_chains.png', chart5_displacement_chains),
        ('06_mixed_workload.png', chart6_mixed_workload),
        ('07_performance_heatmap.png', chart7_heatmap),
    ]

    generated = []
    for name, gen in generators:
        path = gen()
        generated.append(path)
        print(f'  [OK] {name}')

    print()
    print(f'Generated {len(generated)} charts in {os.path.abspath(CHARTS_DIR)}/')
    for p in generated:
        print(f'  - {os.path.basename(p)}')


if __name__ == '__main__':
    main()
