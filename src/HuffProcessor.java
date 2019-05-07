import java.util.*;

/**
 * Interface that all compression suites must implement. That is they must be
 * able to compress a file and also reverse/decompress that process.
 * 
 * @author Brian Lavallee
 * @since 5 November 2015
 * @author Owen Atrachan
 * @since December 1, 2016
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE = HUFF_NUMBER | 1;
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;

	public enum Header {
		TREE_HEADER, COUNT_HEADER
	};

	public Header myHeader = Header.TREE_HEADER;

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {
		int[] counts = readForCounts(in);
		TreeNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		out.writeBits(32, HUFF_TREE);
		writeHeader(root, out);

		in.reset();

		writeCompressedBits(in, codings, out);

	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {
		int id = in.readBits(BITS_PER_INT);
		if (id == HUFF_NUMBER || id == HUFF_TREE) {
			TreeNode root = readTreeHeader(in);
			readCompressedBits(in, out, root);
		} else {
			throw new HuffException("Error, not working");
		}

	}

	public TreeNode readTreeHeader(BitInputStream in) {
		int bitStream = in.readBits(1);

		if (bitStream == 0) {
			// put a value that's not possible bc we are not at a leaf.
			// recurse to go left.
			TreeNode t_first = new TreeNode(-1, 0, readTreeHeader(in), readTreeHeader(in));
			return t_first;
		}

		else {
			TreeNode t_second = new TreeNode(in.readBits(BITS_PER_WORD + 1), 0, null, null);
			return t_second;
		}

	}

	public void readCompressedBits(BitInputStream in, BitOutputStream out, TreeNode t) {
		TreeNode current = t;

		// an infinite loop until hit break.
		while (true) {
			int bitStream = in.readBits(1);

			if (bitStream == -1) {
				throw new HuffException("Error Here");
			}

			if (bitStream == 0) {
				current = current.myLeft; // assign right to the left.
			}

			if (bitStream == 1) {
				current = current.myRight;
			}

			if (current.myLeft == null && current.myRight == null) {
				if (current.myValue == PSEUDO_EOF) {
					break;
				}

				out.writeBits(BITS_PER_WORD, current.myValue);
				// writing 8 bits to out, NOT THE NEXT 8 BITS IN THE STREAM.
				current = t;
			}
		}

	}

	public int[] readForCounts(BitInputStream in) {

		// the readbits just keeps reading the next 8 bits.
		int[] output = new int[ALPH_SIZE];

		while (true) {
			// readBits converts the 8 bits into a value.
			int val = in.readBits(BITS_PER_WORD);

			if (val == -1) {
				break;
			}
			output[val]++;
		}

		return output;

	}

	public TreeNode makeTreeFromCounts(int[] output) {
		PriorityQueue<TreeNode> pq = new PriorityQueue<>();

		for (int k = 0; k < output.length; k++) {
			pq.add(new TreeNode(k, output[k], null, null));
		}
		pq.add(new TreeNode(PSEUDO_EOF, 1, null, null));

		// priority queue is ordered smallest to largest, when making huff tree
		// we want smallest
		// first to create the roots.
		// myWeight is a global variable defined by treeNode class.

		while (pq.size() > 1) {
			// assigning left and right so its not lost.
			// remove takes out the smallest element.
			TreeNode left = pq.remove();
			TreeNode right = pq.remove();
			// value of internal node is not necessary.
			// The left and right established the pointers, as you add the
			// treenode back in
			// to the priority queue, the pointers are already established. so
			// final node has all the
			// pointers
			pq.add(new TreeNode(-1, left.myWeight + right.myWeight, left, right));
		}
		// until there is one left
		TreeNode root = pq.remove();
		return root;

	}

	public void makeCodingsHelper(TreeNode t, String path, String[] codings) {

		if (t.myLeft == null && t.myRight == null) {
			codings[t.myValue] = path;
		}

		else {
			makeCodingsHelper(t.myLeft, path + "0", codings);
			makeCodingsHelper(t.myRight, path + "1", codings);
		}

	}

	public String[] makeCodingsFromTree(TreeNode t) {

		String[] codings = new String[ALPH_SIZE + 1];
		String path = "";

		makeCodingsHelper(t, path, codings);

		return codings;
	}

	public void writeHeader(TreeNode root, BitOutputStream out) {
		// Just to tell that the beginning is a file that can be uncompressed
		// via huffman.
		
		if (root.myLeft != null || root.myRight !=null) {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);

		}

		else if (root.myLeft == null && root.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);

		}

	}

	public void writeCompressedBits(BitInputStream in, String[] codings, BitOutputStream out) {
		while (true) {
			int val = in.readBits(BITS_PER_WORD);

			if (val == -1) {
				out.writeBits(codings[PSEUDO_EOF].length(), Integer.parseInt(codings[PSEUDO_EOF], 2));
				break;
			}
			int num = codings[val].length();
			out.writeBits(num, Integer.parseInt(codings[val], 2));

		}

	}

	public void setHeader(Header header) {
		myHeader = header;
		System.out.println("header set to " + myHeader);
	}
}