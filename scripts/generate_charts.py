#!/usr/bin/env python3
"""
Generate publication-quality charts for Bucketized Cuckoo Hashing with Stash.

Reads CSV data produced by cuckoo.analysis.DirectAnalysis.
Falls back to demo data when CSVs are absent.
"""

import matplotlib.pyplot as plt
import matplotlib.ticker as mticker
import seaborn as sns
import pandas as pd
import numpy as np
import os

# ---------------------------------------------------------------------------
# Style setup
# ---------------------------------------------------------------------------
sns.set_theme(style="whitegrid", font_scale=1.1)
# 8-color palette for 8 variants (tab10 is distinguishable and accessible)
PALETTE = sns.color_palette("tab10", 10)
plt.rcParams.update({
    'figure.figsize': (11, 6), 'figure.dpi': 150,
    'savefig.dpi': 300, 'savefig.bbox': 'tight',
    'axes.titleweight': 'bold',
})

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
RESULTS_DIR = os.path.join(SCRIPT_DIR, '..', 'results')
CHARTS_DIR = os.path.join(RESULTS_DIR, 'charts')
CSV_DIR = os.path.join(RESULTS_DIR, 'csv')
os.makedirs(CHARTS_DIR, exist_ok=True)

# All 8 variants present in DirectAnalysis output (QUADRATIC_PROBING was added
# after DirectAnalysis was last run and is absent from throughput CSVs).
TYPE_ORDER = [
    'STANDARD', 'BUCKETIZED_4', 'STASHED_3', 'D_ARY_3',
    'CHAINING', 'LINEAR_PROBING', 'HOPSCOTCH', 'ROBIN_HOOD',
]
TYPE_LABELS = {
    'STANDARD':       'Standard\nCuckoo',
    'BUCKETIZED_4':   'Bucketized\n(B=4)',
    'STASHED_3':      'Stashed\n(B=4,s=3)',
    'D_ARY_3':        'd-ary\n(d=3)',
    'CHAINING':       'Chaining',
    'LINEAR_PROBING': 'Linear\nProbing',
    'HOPSCOTCH':      'Hopscotch',
    'ROBIN_HOOD':     'Robin\nHood',
}
# Map variant → color index (cuckoo family first, then baselines)
COLOR_MAP = {t: PALETTE[i] for i, t in enumerate(TYPE_ORDER)}


def _try_load_csv(filename):
    path = os.path.join(CSV_DIR, filename)
    if os.path.isfile(path):
        try:
            return pd.read_csv(path)
        except Exception as e:
            print(f"  Warning: could not parse {path}: {e}")
    return None


def _ops_label(x, _=None):
    if x >= 1e6:
        return f'{x/1e6:.1f}M'
    return f'{x/1e3:.0f}K'


# ---------------------------------------------------------------------------
# Chart 1: Insert Throughput (all 8 variants)
# ---------------------------------------------------------------------------
def chart1_insert_throughput():
    df = _try_load_csv('throughput.csv')
    fig, ax = plt.subplots()

    if df is not None:
        ins = df[df['operation'] == 'insert'].copy()
        ins = ins[ins['table_type'].isin(TYPE_ORDER)]
        ins['order'] = ins['table_type'].map({t: i for i, t in enumerate(TYPE_ORDER)})
        ins = ins.sort_values('order')
        colors = [COLOR_MAP[t] for t in ins['table_type']]
        labels = [TYPE_LABELS[t] for t in ins['table_type']]
        bars = ax.bar(labels, ins['ops_per_sec'], color=colors, edgecolor='white', linewidth=0.5)
        ax.bar_label(bars, labels=[_ops_label(v) for v in ins['ops_per_sec']],
                     padding=4, fontsize=8)
    else:
        labels = [TYPE_LABELS[t] for t in TYPE_ORDER]
        vals = [11e6, 5.6e6, 5.5e6, 3.4e6, 7.4e6, 10.3e6, 6.9e6, 1.7e6]
        bars = ax.bar(labels, vals, color=list(COLOR_MAP.values()), edgecolor='white')
        ax.bar_label(bars, labels=[_ops_label(v) for v in vals], padding=4, fontsize=8)

    ax.set_title('Insert Throughput — All 8 Variants (500K Elements)')
    ax.set_ylabel('Throughput (ops/sec)')
    ax.yaxis.set_major_formatter(mticker.FuncFormatter(_ops_label))
    ax.set_ylim(bottom=0)
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '01_insert_throughput.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 2: Lookup Throughput — positive vs negative (all 8 variants)
# ---------------------------------------------------------------------------
def chart2_lookup_throughput():
    df = _try_load_csv('throughput.csv')
    fig, ax = plt.subplots()

    if df is not None:
        pos = df[df['operation'] == 'positive_lookup'].copy()
        neg = df[df['operation'] == 'negative_lookup'].copy()
        pos = pos[pos['table_type'].isin(TYPE_ORDER)]
        neg = neg[neg['table_type'].isin(TYPE_ORDER)]
        pos['order'] = pos['table_type'].map({t: i for i, t in enumerate(TYPE_ORDER)})
        neg['order'] = neg['table_type'].map({t: i for i, t in enumerate(TYPE_ORDER)})
        pos = pos.sort_values('order')
        neg = neg.sort_values('order')
        labels = [TYPE_LABELS[t] for t in pos['table_type']]
        pos_vals = pos['ops_per_sec'].values
        neg_vals = neg['ops_per_sec'].values
    else:
        labels = [TYPE_LABELS[t] for t in TYPE_ORDER]
        pos_vals = [42e6, 13.6e6, 13.1e6, 23.8e6, 26.1e6, 20.1e6, 23.3e6, 14.8e6]
        neg_vals = [17.6e6, 8.6e6, 8.0e6, 10.2e6, 22.3e6, 28.5e6, 25.9e6, 9.9e6]

    x = np.arange(len(labels))
    width = 0.38
    b1 = ax.bar(x - width/2, pos_vals, width, label='Positive lookup', color=PALETTE[0], alpha=0.9, edgecolor='white')
    b2 = ax.bar(x + width/2, neg_vals, width, label='Negative lookup', color=PALETTE[1], alpha=0.9, edgecolor='white')
    ax.bar_label(b1, labels=[_ops_label(v) for v in pos_vals], padding=3, fontsize=7)
    ax.bar_label(b2, labels=[_ops_label(v) for v in neg_vals], padding=3, fontsize=7)
    ax.set_title('Lookup Throughput: Positive vs Negative (All 8 Variants)')
    ax.set_ylabel('Throughput (ops/sec)')
    ax.yaxis.set_major_formatter(mticker.FuncFormatter(_ops_label))
    ax.set_xticks(x)
    ax.set_xticklabels(labels)
    ax.set_ylim(bottom=0)
    ax.legend()
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '02_lookup_throughput.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 3: Load Factor vs Bucket Size
# ---------------------------------------------------------------------------
def chart3_load_factor_vs_bucket():
    df = _try_load_csv('load_factor_vs_bucket.csv')
    fig, ax = plt.subplots(figsize=(8, 5))

    if df is not None:
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
        lf = [0.57, 0.90, 0.98, 0.998]
        ax.plot(bs, lf, marker='o', linewidth=2.5, markersize=10, color=PALETTE[0])
        for b, l in zip(bs, lf):
            ax.annotate(f"{l:.1%}", (b, l), textcoords='offset points',
                        xytext=(0, 14), ha='center', fontweight='bold', fontsize=11)
        ax.set_xticks(bs)

    ax.set_title('Bucketization Raises Load Factor Ceiling (H1)')
    ax.set_xlabel('Bucket Size (B)')
    ax.set_ylabel('Maximum Load Factor')
    ax.set_ylim(0.3, 1.10)
    ax.yaxis.set_major_formatter(mticker.FuncFormatter(lambda y, _: f'{y:.0%}'))
    ax.axhline(y=0.5, color='gray', linestyle='--', alpha=0.5, label='Standard threshold (~50%)')
    ax.legend()
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '03_load_factor_vs_bucket_size.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 4: Rehash Count vs Stash Size
# ---------------------------------------------------------------------------
def chart4_rehash_vs_stash():
    df = _try_load_csv('rehash_vs_stash.csv')
    fig, ax = plt.subplots(figsize=(8, 5))

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

    ax.set_title('Rehash Count vs Stash Size — B=4 (H2)')
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
    fig, ax = plt.subplots(figsize=(8, 5))

    if df is not None and len(df) > 0:
        for i, (ttype, color, label) in enumerate([
            ('Standard',     PALETTE[0], 'Standard Cuckoo'),
            ('Bucketized_B4', PALETTE[2], 'Bucketized (B=4)'),
        ]):
            subset = df[(df['table_type'] == ttype) & (df['chain_length'] > 0)]
            if len(subset) > 0:
                ax.bar(subset['chain_length'] + i * 0.3 - 0.15,
                       subset['count'], width=0.3, alpha=0.8,
                       label=label, color=color, edgecolor='white')
    else:
        np.random.seed(44)
        standard   = np.random.geometric(p=0.35, size=5000)
        bucketized = np.random.geometric(p=0.65, size=5000)
        bins = np.arange(1, max(standard.max(), bucketized.max()) + 2) - 0.5
        ax.hist(standard,   bins=bins, alpha=0.7, label='Standard Cuckoo',
                color=PALETTE[0], edgecolor='white')
        ax.hist(bucketized, bins=bins, alpha=0.7, label='Bucketized (B=4)',
                color=PALETTE[2], edgecolor='white')

    ax.set_title('Displacement Chain Length Distribution')
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
# Chart 6: Mixed Workload Throughput (all 8 variants)
# ---------------------------------------------------------------------------
def chart6_mixed_workload():
    df = _try_load_csv('throughput.csv')
    fig, ax = plt.subplots()

    if df is not None:
        mix = df[df['operation'] == 'mixed'].copy()
        mix = mix[mix['table_type'].isin(TYPE_ORDER)]
        mix['order'] = mix['table_type'].map({t: i for i, t in enumerate(TYPE_ORDER)})
        mix = mix.sort_values('order')
        labels = [TYPE_LABELS[t] for t in mix['table_type']]
        colors = [COLOR_MAP[t] for t in mix['table_type']]
        bars = ax.bar(labels, mix['ops_per_sec'], color=colors, edgecolor='white', linewidth=0.5)
        ax.bar_label(bars, labels=[_ops_label(v) for v in mix['ops_per_sec']],
                     padding=4, fontsize=8)
    else:
        labels = [TYPE_LABELS[t] for t in TYPE_ORDER]
        vals = [9.6e6, 3.9e6, 5.7e6, 12.6e6, 11.6e6, 14.8e6, 13.2e6, 8.9e6]
        colors = list(COLOR_MAP.values())
        bars = ax.bar(labels, vals, color=colors, edgecolor='white')
        ax.bar_label(bars, labels=[_ops_label(v) for v in vals], padding=4, fontsize=8)

    ax.set_title('Mixed Workload Throughput — All 8 Variants (80% Read, 20% Write)')
    ax.set_ylabel('Throughput (ops/sec)')
    ax.yaxis.set_major_formatter(mticker.FuncFormatter(_ops_label))
    ax.set_ylim(bottom=0)
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '06_mixed_workload.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 7: Delete Throughput (all 8 variants)  [NEW]
# ---------------------------------------------------------------------------
def chart7_delete_throughput():
    df = _try_load_csv('delete_throughput.csv')
    fig, ax = plt.subplots()

    if df is not None:
        df = df[df['table_type'].isin(TYPE_ORDER)].copy()
        df['order'] = df['table_type'].map({t: i for i, t in enumerate(TYPE_ORDER)})
        df = df.sort_values('order')
        labels = [TYPE_LABELS[t] for t in df['table_type']]
        colors = [COLOR_MAP[t] for t in df['table_type']]
        bars = ax.bar(labels, df['delete_ops_per_sec'], color=colors, edgecolor='white', linewidth=0.5)
        ax.bar_label(bars, labels=[_ops_label(v) for v in df['delete_ops_per_sec']],
                     padding=4, fontsize=8)
    else:
        labels = [TYPE_LABELS[t] for t in TYPE_ORDER]
        vals = [30e6, 10.5e6, 10e6, 18.6e6, 37.2e6, 14.7e6, 47.3e6, 14.6e6]
        bars = ax.bar(labels, vals, color=list(COLOR_MAP.values()), edgecolor='white')
        ax.bar_label(bars, labels=[_ops_label(v) for v in vals], padding=4, fontsize=8)

    ax.set_title('Delete Throughput — All 8 Variants (O(1) Tombstone-Free for Cuckoo)')
    ax.set_ylabel('Throughput (ops/sec)')
    ax.yaxis.set_major_formatter(mticker.FuncFormatter(_ops_label))
    ax.set_ylim(bottom=0)
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '07_delete_throughput.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 8: Hash Function Sensitivity  [NEW]
# ---------------------------------------------------------------------------
def chart8_hash_sensitivity():
    df = _try_load_csv('hash_sensitivity.csv')
    fig, axes = plt.subplots(1, 2, figsize=(12, 5))

    if df is not None:
        funcs = df['hash_function'].unique()
        func_colors = {f: PALETTE[i] for i, f in enumerate(funcs)}

        # Left: avg displacement chain per hash function (box plot)
        data_by_func = [df[df['hash_function'] == f]['avg_displacement_chain'].values for f in funcs]
        bp = axes[0].boxplot(data_by_func, tick_labels=funcs, patch_artist=True)
        for patch, f in zip(bp['boxes'], funcs):
            patch.set_facecolor(func_colors[f])
            patch.set_alpha(0.7)
        axes[0].set_title('Avg Displacement Chain by Hash Function')
        axes[0].set_ylabel('Avg Displacement Chain Length')
        axes[0].set_xlabel('Hash Function')

        # Right: max load factor achieved per hash function
        avg_lf = df.groupby('hash_function')['max_load_factor'].mean()
        std_lf = df.groupby('hash_function')['max_load_factor'].std()
        colors = [func_colors[f] for f in avg_lf.index]
        bars = axes[1].bar(avg_lf.index, avg_lf.values, color=colors,
                           yerr=std_lf.values, capsize=5, edgecolor='white', alpha=0.85)
        axes[1].bar_label(bars, labels=[f'{v:.2%}' for v in avg_lf.values],
                          padding=4, fontsize=9)
        axes[1].set_title('Max Load Factor Achieved by Hash Function')
        axes[1].set_ylabel('Max Load Factor')
        axes[1].yaxis.set_major_formatter(mticker.FuncFormatter(lambda y, _: f'{y:.0%}'))
        axes[1].set_ylim(0, 1.05)
        axes[1].set_xlabel('Hash Function')
    else:
        funcs = ['MurmurHash3', 'xxHash32', 'FNV1a', 'Universal']
        axes[0].bar(funcs, [0.15, 0.15, 0.15, 0.15], color=PALETTE[:4])
        axes[0].set_title('Avg Displacement Chain by Hash Function')
        axes[1].bar(funcs, [0.90, 0.90, 0.90, 0.90], color=PALETTE[:4])
        axes[1].set_title('Max Load Factor by Hash Function')

    fig.suptitle('Hash Function Sensitivity — incl. Carter-Wegman Universal (H5)',
                 fontweight='bold')
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '08_hash_sensitivity.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 9: d-ary Load Factors  [NEW]
# ---------------------------------------------------------------------------
def chart9_dary_load_factors():
    df = _try_load_csv('dary_load_factors.csv')
    fig, ax = plt.subplots(figsize=(8, 5))

    if df is not None:
        avg = df.groupby('d')['max_load_factor'].mean().reset_index()
        std = df.groupby('d')['max_load_factor'].std().reset_index()
        d_colors = [PALETTE[i] for i in range(len(avg))]
        bars = ax.bar(avg['d'].astype(str), avg['max_load_factor'],
                      yerr=std['max_load_factor'], color=d_colors,
                      capsize=6, edgecolor='white', alpha=0.85, width=0.5)
        ax.bar_label(bars, labels=[f'{v:.1%}' for v in avg['max_load_factor']],
                     padding=6, fontweight='bold', fontsize=11)
        ax.set_xticks(range(len(avg)))
        ax.set_xticklabels([f'd = {int(d)}' for d in avg['d']], fontsize=11)
    else:
        ds = ['d = 2', 'd = 3', 'd = 4']
        lf = [0.57, 0.89, 0.96]
        bars = ax.bar(ds, lf, color=PALETTE[:3], edgecolor='white', width=0.5)
        ax.bar_label(bars, labels=[f'{v:.0%}' for v in lf], padding=6, fontweight='bold', fontsize=11)

    ax.set_title('d-ary Cuckoo: Max Load Factor vs Number of Hash Functions')
    ax.set_xlabel('Number of Hash Functions (d)')
    ax.set_ylabel('Maximum Load Factor')
    ax.set_ylim(0, 1.10)
    ax.yaxis.set_major_formatter(mticker.FuncFormatter(lambda y, _: f'{y:.0%}'))
    ax.axhline(y=0.5, color='gray', linestyle='--', alpha=0.4, label='Standard cuckoo baseline (~50%)')
    ax.legend()
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '09_dary_load_factors.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 10: Performance Summary Heatmap (all 8 variants)
# ---------------------------------------------------------------------------
def chart10_heatmap():
    df = _try_load_csv('throughput.csv')
    del_df = _try_load_csv('delete_throughput.csv')
    fig, ax = plt.subplots(figsize=(14, 6))

    if df is not None:
        pivot = df[df['table_type'].isin(TYPE_ORDER)].pivot(
            index='table_type', columns='operation', values='ops_per_sec')
        col_map = {
            'insert':          'Insert',
            'positive_lookup': 'Pos Lookup',
            'negative_lookup': 'Neg Lookup',
            'mixed':           'Mixed',
        }
        pivot = pivot.rename(columns=col_map)

        if del_df is not None:
            del_series = del_df[del_df['table_type'].isin(TYPE_ORDER)].set_index('table_type')['delete_ops_per_sec']
            pivot['Delete'] = del_series

        row_order = [t for t in TYPE_ORDER if t in pivot.index]
        pivot = pivot.loc[row_order]
        pivot.index = [TYPE_LABELS[t].replace('\n', ' ') for t in row_order]

        annot_text = np.array([[_ops_label(v) for v in row] for row in pivot.values])
        normalized = pivot.apply(lambda col: (col - col.min()) / (col.max() - col.min() + 1e-9))
        sns.heatmap(normalized, annot=annot_text, fmt='', cmap='YlGnBu',
                    linewidths=0.5, ax=ax, cbar_kws={'label': 'Relative Score (column-normalized)'})
    else:
        data = {
            'Insert':     [11e6, 5.6e6, 5.5e6, 3.4e6, 7.4e6, 10.3e6, 6.9e6, 1.7e6],
            'Pos Lookup': [42e6, 13.6e6, 13.1e6, 23.8e6, 26.1e6, 20.1e6, 23.3e6, 14.8e6],
            'Neg Lookup': [17.6e6, 8.6e6, 8.0e6, 10.2e6, 22.3e6, 28.5e6, 25.9e6, 9.9e6],
            'Mixed':      [9.6e6, 3.9e6, 5.7e6, 12.6e6, 11.6e6, 14.8e6, 13.2e6, 8.9e6],
            'Delete':     [30e6, 10.5e6, 10e6, 18.6e6, 37.2e6, 14.7e6, 47.3e6, 14.6e6],
        }
        labels = [TYPE_LABELS[t].replace('\n', ' ') for t in TYPE_ORDER]
        hm_df = pd.DataFrame(data, index=labels)
        annot = np.array([[_ops_label(v) for v in row] for row in hm_df.values])
        normalized = hm_df.apply(lambda col: (col - col.min()) / (col.max() - col.min() + 1e-9))
        sns.heatmap(normalized, annot=annot, fmt='', cmap='YlGnBu',
                    linewidths=0.5, ax=ax, cbar_kws={'label': 'Relative Score'})

    ax.set_title('Performance Summary Heatmap — All 8 Variants')
    ax.set_ylabel('')
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '10_performance_heatmap.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Chart 11: Perfect Hashing vs Dynamic Variants (Lookup)  [NEW]
# ---------------------------------------------------------------------------
def chart11_perfect_hash_lookup():
    df = _try_load_csv('perfect_hash_lookup.csv')
    fig, ax = plt.subplots()

    pretty = {
        'PERFECT':          'Perfect\n(FKS, static)',
        'STANDARD':         'Standard\nCuckoo',
        'BUCKETIZED_4':     'Bucketized\n(B=4)',
        'LINEAR_PROBING':   'Linear\nProbing',
        'QUADRATIC_PROBING':'Quadratic\nProbing',
        'HOPSCOTCH':        'Hopscotch',
        'ROBIN_HOOD':       'Robin\nHood',
    }
    order = ['PERFECT', 'STANDARD', 'BUCKETIZED_4', 'LINEAR_PROBING',
             'QUADRATIC_PROBING', 'HOPSCOTCH', 'ROBIN_HOOD']

    if df is not None:
        df = df[df['table_type'].isin(order)].copy()
        df['o'] = df['table_type'].map({t: i for i, t in enumerate(order)})
        df = df.sort_values('o')
        labels = [pretty[t] for t in df['table_type']]
        # Highlight PERFECT in a contrasting color
        colors = ['#D62728' if t == 'PERFECT' else PALETTE[i % len(PALETTE)]
                  for i, t in enumerate(df['table_type'])]
        bars = ax.bar(labels, df['lookup_ops_per_sec'], color=colors,
                      edgecolor='white', linewidth=0.5)
        ax.bar_label(bars, labels=[_ops_label(v) for v in df['lookup_ops_per_sec']],
                     padding=4, fontsize=8)
    else:
        labels = [pretty[t] for t in order]
        vals = [32e6, 34e6, 7.5e6, 12e6, 31e6, 29e6, 22e6]
        bars = ax.bar(labels, vals, color=PALETTE[:len(labels)], edgecolor='white')
        ax.bar_label(bars, labels=[_ops_label(v) for v in vals], padding=4, fontsize=8)

    ax.set_title('Perfect Hashing vs Dynamic Variants — Lookup Throughput')
    ax.set_ylabel('Lookup Throughput (ops/sec)')
    ax.yaxis.set_major_formatter(mticker.FuncFormatter(_ops_label))
    ax.set_ylim(bottom=0)
    plt.tight_layout()
    path = os.path.join(CHARTS_DIR, '11_perfect_hash_lookup.png')
    fig.savefig(path)
    plt.close(fig)
    return path


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    print('Bucketized Cuckoo with Stash — Chart Generator')
    print('=' * 52)

    csvs = ['throughput.csv', 'load_factor_vs_bucket.csv', 'rehash_vs_stash.csv',
            'displacement_chains.csv', 'delete_throughput.csv', 'hash_sensitivity.csv',
            'dary_load_factors.csv', 'perfect_hash_lookup.csv']
    found = [f for f in csvs if os.path.isfile(os.path.join(CSV_DIR, f))]
    missing = [f for f in csvs if f not in found]
    if found:
        print(f'  CSVs found    : {", ".join(found)}')
    if missing:
        print(f'  CSVs missing  : {", ".join(missing)} (demo data used)')

    generators = [
        ('01_insert_throughput.png',        chart1_insert_throughput),
        ('02_lookup_throughput.png',         chart2_lookup_throughput),
        ('03_load_factor_vs_bucket_size.png', chart3_load_factor_vs_bucket),
        ('04_rehash_vs_stash.png',           chart4_rehash_vs_stash),
        ('05_displacement_chains.png',       chart5_displacement_chains),
        ('06_mixed_workload.png',            chart6_mixed_workload),
        ('07_delete_throughput.png',         chart7_delete_throughput),
        ('08_hash_sensitivity.png',          chart8_hash_sensitivity),
        ('09_dary_load_factors.png',         chart9_dary_load_factors),
        ('10_performance_heatmap.png',       chart10_heatmap),
        ('11_perfect_hash_lookup.png',       chart11_perfect_hash_lookup),
    ]

    generated = []
    for name, gen in generators:
        path = gen()
        generated.append(path)
        print(f'  [OK] {name}')

    print()
    print(f'Generated {len(generated)} charts → {os.path.abspath(CHARTS_DIR)}/')


if __name__ == '__main__':
    main()
