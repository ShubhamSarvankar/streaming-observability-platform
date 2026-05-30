import sys
from graph import build_graph


def main():
    graph = build_graph()
    print("ask a question (ctrl-d to quit):")
    for line in sys.stdin:
        q = line.strip()
        if not q:
            continue
        final = graph.invoke({"question": q})
        print("\n" + (final.get("answer") or "(no answer)") + "\n")


if __name__ == "__main__":
    main()
