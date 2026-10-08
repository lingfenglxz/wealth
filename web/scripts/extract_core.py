"""Extract only the pure SSQ dependency closure; never include routes or runtime data."""
import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / 'web/public/ssq_core.py'

def extract():
    source = ast.parse((ROOT / 'server/main.py').read_text(encoding='utf-8-sig'))
    definitions = {}
    imports = []
    for node in source.body:
        if isinstance(node, (ast.Import, ast.ImportFrom)):
            module = node.module if isinstance(node, ast.ImportFrom) else node.names[0].name
            if module not in ('httpx', 'fastapi'):
                imports.append(node)
        elif isinstance(node, (ast.FunctionDef, ast.ClassDef)):
            if not node.decorator_list or isinstance(node, ast.ClassDef):
                definitions[node.name] = node
        elif isinstance(node, ast.Assign):
            for name in node.targets:
                if isinstance(name, ast.Name):
                    definitions[name.id] = node
    wanted = set()
    pending = ['analyze', 'recommend', 'build_number_rankings', 'build_ssq_model_evaluation',
               'compute_fusion_weights', 'compute_fused_selection_score', 'build_narrative',
               'build_server_settlement']
    while pending:
        name = pending.pop()
        if name not in definitions or name in wanted:
            continue
        wanted.add(name)
        pending.extend(n.id for n in ast.walk(definitions[name]) if isinstance(n, ast.Name))
    nodes = imports + [n for n in source.body if n in [definitions[k] for k in wanted]]
    text = '# Generated from server/main.py by scripts/extract_core.py.\n' + ast.unparse(ast.Module(body=nodes, type_ignores=[])) + '\n'
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(text, encoding='utf-8', newline='\n')
    return text

if __name__ == '__main__':
    extract()
