#!/usr/bin/env python3
"""Style checker for the plugin's coding standards.

Enforces the mechanically checkable subset of the project's style rules over
every Java source file:

  1. No line wider than 120 columns (tabs count as 4).
  2. No inlined control statements (`if (x) foo();` on one line).
  3. No blank line directly after `{` or directly before `}`.
  4. No one-line method/lambda bodies containing a statement (`{ return x; }`).
  5. Brace uniformity across if/else chains (all branches braced or none).
  6. Brace uniformity across nesting (a braceless outer control statement may
     not wrap a braced inner one).
  7. Allman lambdas: a multi-statement lambda body's `{` goes on the next line.
  8. No wildcard imports.
  9. Statement-level method chains of three or more links must be wrapped.
 10. No inline `//` comments (Javadoc instead), except a note that is the sole
     content of an intentionally-empty `catch` block.
 11. Every class/interface/enum declaration (including nested) carries Javadoc.
 12. No plain `/* ... */` block comments — the only permitted block comments
     are `/**` Javadoc and the license header opening on line 1.
 13. No access-bypass reflection (RuneLite plugin guideline): `setAccessible`,
     `sun.misc.Unsafe`/`jdk.internal.*`, reflective `getDeclaredMethod`/
     `getDeclaredConstructor` lookups, or `Class.forName`. Gson `TypeToken`
     generics (`java.lang.reflect.Type`/`ParameterizedType`) and the schema
     snapshot test's structural `getDeclaredFields()` inspection are deliberately
     exempt — they read type/field shape, they don't defeat access modifiers.
 14. No braces on a single-statement control body: an `if`/`for`/`while`/`else`
     governing one simple statement omits its braces. Braces are kept (and not
     flagged) for a multi-statement body, a variable declaration, a body that is
     itself a control statement, and any if/else chain with a braced branch (rule
     5 keeps the whole chain uniform).
 15. A blank line follows every control construct, unless the next line is a
     closing brace, a chain continuation (`else`/`catch`/`finally`), or blank.
 16. Imports are grouped java/javax, then other third-party, then net.runelite,
     then static; groups are separated by a single blank line, no blank line
     falls inside a group, and each group is alphabetized.

Not mechanized (judgement calls, enforced by review): the Stream-API preference,
the two-tab continuation indent and ternary-break shape (both already bounded by
the 120-column limit), Javadoc wording quality, and the shared-colour-constant
rule (every panel colour comes from StockpileColors).

Exits non-zero listing every violation, or prints a summary and exits zero.
"""

import re
import sys
import glob

TAB = 4
MAX_WIDTH = 120
CONTROL = r'(?:if|for|while|switch|synchronized)'

# Reflection primitives that bypass access control or the constructor contract.
# Structural inspection (getDeclaredFields/Modifier) and TypeToken generics are
# intentionally absent — see rule 13 in the module docstring.
REFLECTION_BANS = [
    (r'\bimport\s+sun\.misc\.', 'sun.misc'),
    (r'\bsun\.misc\.Unsafe\b', 'sun.misc.Unsafe'),
    (r'\bimport\s+jdk\.internal\.', 'jdk.internal'),
    (r'\.setAccessible\s*\(', 'setAccessible'),
    (r'\bClass\.forName\s*\(', 'Class.forName'),
    (r'\.getDeclaredMethod\s*\(', 'getDeclaredMethod'),
    (r'\.getDeclaredConstructor\s*\(', 'getDeclaredConstructor'),
]

violations = []


def report(path, lineno, rule, text):
    violations.append(f"{path}:{lineno}: [{rule}] {text.strip()[:100]}")


def strip_strings(line):
    """Blank out string/char literal contents so their symbols don't confuse checks."""
    out = []
    quote = None
    i = 0
    while i < len(line):
        c = line[i]
        if quote:
            if c == '\\':
                out.append('..')
                i += 2
                continue
            if c == quote:
                quote = None
                out.append(c)
            else:
                out.append('.')
        else:
            if c in '"\'':
                quote = c
            out.append(c)
        i += 1
    return ''.join(out)


def indent_of(line):
    return len(line) - len(line.lstrip('\t'))


def width_of(line):
    tabs = indent_of(line)
    return tabs * TAB + (len(line) - tabs)


def in_block_comment_mask(lines):
    """Per-line flag for lines lying inside /* ... */ block comments."""
    mask = []
    inside = False
    for line in lines:
        started_inside = inside
        stripped = strip_strings(line)
        if inside and '*/' in stripped:
            inside = False
        elif not inside and '/*' in stripped and '*/' not in stripped.split('/*', 1)[1]:
            inside = True
        mask.append(started_inside or line.lstrip('\t ').startswith('*'))
    return mask


def header_end(lines, i):
    """Index of the line where the control header starting at i balances its parens."""
    bal = 0
    j = i
    while j < len(lines):
        s = strip_strings(lines[j])
        bal += s.count('(') - s.count(')')
        if bal <= 0:
            return j
        j += 1
    return j


def next_code_line(lines, j):
    k = j + 1
    while k < len(lines) and lines[k].strip() == '':
        k += 1
    return k


def allowed_empty_catch_note(lines, i):
    """Whether the // comment on 0-based line i is the sole content of an empty catch block."""
    bal = 0
    j = i - 1
    while j >= 0:
        s = strip_strings(lines[j]).split('//')[0]
        bal += s.count('}') - s.count('{')
        if bal < 0:
            break

        j -= 1

    if j < 0:
        return False

    header = strip_strings(lines[j]).split('//')[0].strip()
    if header == '{' and j > 0:
        header = strip_strings(lines[j - 1]).split('//')[0].strip()

    if not header.startswith('catch'):
        return False

    k = j + 1
    while k < len(lines):
        s = strip_strings(lines[k]).split('//')[0].strip()
        if s.startswith('}'):
            return True

        content = lines[k].strip()
        if content and not content.startswith('//'):
            return False

        k += 1

    return False


def javadoc_precedes(lines, i):
    """Whether a /** ... */ block sits directly above 0-based declaration line i, skipping annotations."""
    rev_bal = 0
    j = i - 1
    while j >= 0:
        s = strip_strings(lines[j]).strip()
        if s.endswith('*/'):
            return True

        rev_bal += s.count(')') - s.count('(')
        if rev_bal > 0 or (s.startswith('@') and rev_bal == 0):
            if s.startswith('@'):
                rev_bal = 0

            j -= 1
            continue

        return False

    return False


def match_brace(lines, k, comment):
    """Given k is the line bearing the opening '{', return the index of its matching '}' line."""
    depth = 0
    j = k
    while j < len(lines):
        if not comment[j]:
            s = strip_strings(lines[j]).split('//')[0]
            depth += s.count('{') - s.count('}')
            if depth <= 0:
                return j

        j += 1
    return j


def is_simple_statement(s):
    """Whether s is a single simple statement (ends in ';', not a control statement, not a block)."""
    if not s.endswith(';') or '{' in s:
        return False

    return not re.match(r'^(if|for|while|do|switch|try|synchronized|else)\b', s)


def is_declaration(s):
    """Whether s is a local variable declaration, which keeps its braces as a control body."""
    if re.match(r'^(return|throw|assert|break|continue|yield|super|this|new)\b', s):
        return False

    return bool(re.match(r'^(final\s+)?[A-Za-z_$][\w.$]*(\s*<[^;=]*>)?(\s*\[\s*\])?\s+[A-Za-z_$]\w*\s*[=;]', s))


def braceless_end(lines, k, comment):
    """Last physical line of a braceless control body beginning at line k.

    A braceless body is a single governed statement that may still span several physical lines — a
    wrapped method chain, a broken argument list, or a nested control statement. Its end is where that
    statement completes, not merely its first line, so the rule-3 blank-line check looks past the whole
    construct rather than mistaking a mid-statement continuation for a missing blank.
    """
    s = strip_strings(lines[k]).split('//')[0].strip()
    if re.match(r'^(if|for|while|switch|synchronized)\b', s) and '(' in s:
        inner = next_code_line(lines, header_end(lines, k))
        if inner >= len(lines):
            return k

        if lines[inner].strip().startswith('{'):
            return match_brace(lines, inner, comment)

        return braceless_end(lines, inner, comment)

    j = k
    while j < len(lines):
        code = strip_strings(lines[j]).split('//')[0].rstrip()
        if code.endswith('{'):
            return match_brace(lines, j, comment)

        if code.endswith(';'):
            return j

        j += 1

    return k


def control_branches(lines, comment):
    """Every control header, grouped into if/else-if/else chains (for/while/if each start a chain).

    Each branch is (start_index, braced, single_simple, end_index): whether it is braced, whether its
    body is a single simple statement (so braces are optional), and the last line of its body/block.
    """
    branches = []
    for idx in range(len(lines)):
        if comment[idx]:
            continue

        s = lines[idx].strip()
        if re.match(r'^if\s*\(', s):
            kind = 'if'
        elif re.match(r'^else if\s*\(', s):
            kind = 'elseif'
        elif s == 'else':
            kind = 'else'
        elif re.match(r'^for\s*\(', s):
            kind = 'for'
        elif re.match(r'^while\s*\(', s):
            kind = 'while'
        else:
            continue

        ind = indent_of(lines[idx])
        j = header_end(lines, idx) if '(' in s else idx
        k = next_code_line(lines, j)
        if k >= len(lines):
            continue

        braced = lines[k].strip().startswith('{')
        single = False
        if braced:
            end = match_brace(lines, k, comment)
            body = lines[k + 1].strip() if k + 1 < len(lines) else ''
            single = end == k + 2 and is_simple_statement(body) and not is_declaration(body)
        else:
            end = braceless_end(lines, k, comment)

        branches.append((idx, ind, kind, braced, single, end))

    chains = []
    for branch in branches:
        idx, ind, kind, braced, single, end = branch
        if kind in ('if', 'for', 'while'):
            chains.append([branch])
            continue

        # An else/else-if joins the most recent chain opened at its own indent — not simply the last
        # chain, which a nested control statement inside the preceding branch would otherwise be.
        target = next((c for c in reversed(chains) if indent_of(lines[c[0][0]]) == ind), None)
        if target is not None:
            target.append(branch)
        else:
            chains.append([branch])

    return chains


def check_file(path):
    lines = open(path).read().split('\n')
    comment = in_block_comment_mask(lines)

    for i, raw in enumerate(lines, 1):
        if width_of(raw) > MAX_WIDTH:
            report(path, i, 'width>120', raw)

    prev = ''
    for i, raw in enumerate(lines, 1):
        if raw.strip() == '' and prev.strip().endswith('{') and prev.strip() != '':
            report(path, i, 'blank-after-brace', prev)

        if re.match(r'^[\t ]*\}[,;)]*$', raw) and prev.strip() == '' and i > 1:
            report(path, i, 'blank-before-brace', raw)

        prev = raw

    for i, raw in enumerate(lines, 1):
        if comment[i - 1]:
            continue

        line = strip_strings(raw)
        code = line.split('//')[0]

        if re.match(r'^import .*\*;$', code.strip()):
            report(path, i, 'wildcard-import', raw)

        for pattern, name in REFLECTION_BANS:
            if re.search(pattern, code):
                report(path, i, 'reflection', f"{name}: {raw}")
                break

        if '//' in line and not allowed_empty_catch_note(lines, i - 1):
            report(path, i, 'inline-comment', raw)

        if re.search(r'/\*(?!\*)', line.split('//')[0]) and i > 1:
            report(path, i, 'block-comment', raw)

        stripped = code.strip()

        if (re.match(r'^(?:(?:public|protected|private|static|final|abstract)\s+)*(?:class|interface|enum)\s+[A-Z]', stripped)
                and not javadoc_precedes(lines, i - 1)):
            report(path, i, 'missing-class-javadoc', raw)

        if re.match(r'^' + CONTROL + r'\s*\(', stripped):
            bal = stripped.count('(') - stripped.count(')')
            if bal == 0:
                after = re.sub(r'^' + CONTROL + r'\s*', '', stripped)
                depth = 0
                rest = ''
                for idx, c in enumerate(after):
                    if c == '(':
                        depth += 1
                    elif c == ')':
                        depth -= 1
                        if depth == 0:
                            rest = after[idx + 1:].strip()
                            break
                if rest and rest not in ('{',) and not rest.startswith('//'):
                    report(path, i, 'inline-control', raw)

        if re.search(r'\)\s*\{[^{}]*;[^{}]*\}', code) or re.search(r'->\s*\{[^{}]*;[^{}]*\}', code):
            report(path, i, 'one-line-body', raw)

        if re.search(r'->\s*\{\s*$', code):
            report(path, i, 'inline-lambda-brace', raw)

        if (stripped.endswith(';') and not re.match(r'^(if|for|while|else|case|default|do|switch|import|package)\b', stripped)
                and '->' not in code and '||' not in code and '&&' not in code and '+' not in code):
            hops = len(re.findall(r'\)\.[A-Za-z_]', code))
            first_hop = code.find(').')
            base_is_call = first_hop >= 0 and re.search(r'\bnew\s', code[:first_hop])
            links = hops if base_is_call else hops + 1
            if hops >= 2 and links >= 3:
                report(path, i, 'unwrapped-chain', raw)

    # if/else chain and nesting uniformity
    chains = []
    for i in range(len(lines)):
        if comment[i]:
            continue

        s = lines[i].strip()
        kind = None
        if re.match(r'^if\s*\(', s):
            kind = 'if'
        elif s == 'else' or re.match(r'^else if\s*\(', s):
            kind = 'else'

        if not kind:
            continue

        ind = indent_of(lines[i])
        j = header_end(lines, i) if '(' in s else i
        k = next_code_line(lines, j)
        braced = k < len(lines) and lines[k].strip().startswith('{')

        if kind == 'if':
            chains.append((i + 1, ind, [braced]))
        elif chains and indent_of(lines[chains[-1][0] - 1]) == ind:
            chains[-1][2].append(braced)

        if not braced and k < len(lines):
            body = lines[k].strip()
            if re.match(r'^(?:if|for|while|switch|try|synchronized)\b', body) and indent_of(lines[k]) == ind + 1:
                j2 = header_end(lines, k) if '(' in body else k
                k2 = next_code_line(lines, j2)
                if k2 < len(lines) and lines[k2].strip().startswith('{'):
                    report(path, i + 1, 'braceless-outer-braced-inner', lines[i])

    for start, _, flags in chains:
        if len(flags) > 1 and len(set(flags)) > 1:
            report(path, start, 'mixed-chain-braces', lines[start - 1])

    # Rules 1 and 3, over grouped control chains.
    for chain in control_branches(lines, comment):
        # Rule 1: a chain that needs braces nowhere (no braced multi-statement branch) must be
        # braceless in every branch; a braced single-statement branch is a violation.
        required = any(braced and not single for (_, _, _, braced, single, _) in chain)
        if not required:
            for (idx, _, _, braced, single, _) in chain:
                if braced and single:
                    report(path, idx + 1, 'braces-on-single-statement', lines[idx])

        # Rule 3: a blank line must follow the whole construct, unless the next line is a closing
        # brace, a chain continuation (else/catch/finally), or already blank.
        end = chain[-1][5]
        m = end + 1
        if m < len(lines) and not comment[end]:
            nxt = lines[m].strip()
            if nxt != '' and not nxt.startswith('}') and not re.match(r'^(else|catch|finally)\b', nxt):
                report(path, end + 1, 'missing-blank-after-control', lines[end])

    # Rule 10: imports are grouped (java/javax, third-party, net.runelite, static) and each group is
    # alphabetized by path, groups separated by a single blank line.
    check_import_order(path, lines)


def import_group(imp):
    """Sort bucket for an import line: 0 java/javax, 2 net.runelite, 3 static, 1 everything else."""
    body = imp[len('import '):].strip()
    if body.startswith('static '):
        return 3

    if re.match(r'^(java|javax)\.', body):
        return 0

    if body.startswith('net.runelite.'):
        return 2

    return 1


def check_import_order(path, lines):
    idxs = [i for i, l in enumerate(lines) if l.startswith('import ')]
    if not idxs:
        return

    prev_group = None
    prev_import = None
    prev_i = None
    for i in range(idxs[0], idxs[-1] + 1):
        line = lines[i]
        if line.strip() == '':
            continue

        if not line.startswith('import '):
            report(path, i + 1, 'import-block-interrupted', line)
            continue

        g = import_group(line)
        body = line.strip()
        # Order by the dotted path, not the raw line: the trailing ';' (0x3B) otherwise sorts after a
        # digit (0x32), wrongly ranking `Graphics2D;` ahead of the shorter-prefix `Graphics;`.
        key = body[:-1] if body.endswith(';') else body
        if prev_group is not None:
            gap = i - prev_i - 1
            if g < prev_group:
                report(path, i + 1, 'import-grouping', body)
            elif g == prev_group:
                if key < prev_import:
                    report(path, i + 1, 'import-order', body)

                if gap != 0:
                    report(path, i + 1, 'import-blank-within-group', body)
            elif gap != 1:
                report(path, i + 1, 'import-group-separator', body)

        prev_group, prev_import, prev_i = g, key, i


def main():
    files = sorted(glob.glob('src/main/java/**/*.java', recursive=True)
            + glob.glob('src/test/java/**/*.java', recursive=True))
    for path in files:
        check_file(path)

    if violations:
        print(f"Style check FAILED: {len(violations)} violation(s)")
        for v in violations:
            print("  " + v)

        sys.exit(1)

    print(f"Style check passed ({len(files)} files).")


if __name__ == '__main__':
    main()
