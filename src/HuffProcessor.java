import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		// Determine the frequency of each character
		int[] counts = readForCounts(in);
		//Create the Huffman Tree
		HuffNode root = makeTreeFromCounts(counts);
		//Create the encodings for each character
		String[] codings = makeCodingsFromTree(root);
		// Write the magic number and the tree to the beginning/header of the compressed file
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeTree(root,out);
		
		//Read the file again and write the encoding for each eight-bit chunk
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}

	/**
	 * Determine the frequency of every 8-bit character
	 * @param in 
	 *            Buffered bit stream of the file to be compressed.
	 * @return 
	 * 			array of integers that represent the frequency of all characters. 
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] test = new int[ALPH_SIZE + 1];
		while(true) {
			int index = in.readBits(BITS_PER_WORD);
			if (index == -1) break;
			test[index] ++;
		}
		test[PSEUDO_EOF] = 1;
		return test;
	}
	
	/**
	 * Create the Huffman tree used to create encodings
	 * @param counts
	 * 				array of integers that contains the frequency of all characters.
	 * @return
	 * 			 A Huffman tree 
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		int k = 0;
		for(int i : counts) {
			if (i > 0) {
				pq.add(new HuffNode(k,i,null,null));
			}
			k++;
		}
		
		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1,left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	/**
	 * Creates the encodings for each eight-bit character
	 * @param root
	 * 				The starting node for traversing the tree
	 * @return 
	 *         a string array containing the encodings of the 8-bit chunks
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		helper(root, "", encodings);
		return encodings;
	}
	/**
	 * Helper method for makeCodingsFromTree. Modifies the string array so that 
	 * it contains the encodings for each character. 
	 * @param root
	 * @param string
	 * 				 A string representing the path to the current node root
	 * @param encodings
	 */
	private void helper(HuffNode root, String string, String[] encodings) {
		if(root == null) return;
		
		if(root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = string;
			return;
		}
		
		helper(root.myLeft, string + "0", encodings);
		helper(root.myRight, string + "1", encodings);
	}
	/**
	 * Writes the tree of the compressed file
	 * @param root
	 * @param out
	 */
	private void writeTree(HuffNode root, BitOutputStream out) {
		if (root.myLeft == null && root.myRight == null) {
			out.write(1);
			out.write(BITS_PER_WORD + 1);
		}
		else {
			out.write(0);
			writeTree(root.myLeft, out);
			writeTree(root.myRight, out);
		}
	}
	
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		// TODO Auto-generated method stub
		while(in.readBits(BITS_PER_WORD) != -1) {
			String code = codings[in.readBits(BITS_PER_WORD)];
			out.writeBits(code.length(), Integer.parseInt(code,2));
			in.readBits(BITS_PER_WORD);
		}
		//String code;
		//for (int k =0; k < codings.length; k++) {
			//code = codings[k];
			//out.writeBits(code.length(), Integer.parseInt(code,2));
		//}
		String end = codings[PSEUDO_EOF];
		out.writeBits(end.length(), Integer.parseInt(end,2));
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
	public void decompress(BitInputStream in, BitOutputStream out){
		// Read the 32-bit number and check that the file is Huffman-coded
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with"+bits);
		}
		// Read the tree used to decompress and compress
		HuffNode root = readTreeHeader(in);
		// Read the bits from the compressed file and traverse root-to-leaf paths and write leaf values to the output files.
		readCompressedBits(root,in,out);
		
		// Close the output file
		out.close();
	}

	private HuffNode readTreeHeader(BitInputStream in) {
		/** Read a single bit at a time
		 * Throw an error if bit is not readable 
		 */
		int bit = in.readBits(1);
		if(bit == -1) {
			throw new HuffException("illegal value"+bit);
		}
		//If the node is an internal node, read the left and right trees.
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		//When a leaf node is reached, read the bits that make up the path to it. 
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value,0,null,null);
		}
	}
	
	/**
	 * Traverse the tree from the root and go left if zero is read
	 * and go right if a one is read. 
	 * 
	 * @param root
	 * 			   The starting node for traversing the tree
	 * 
	 * @param in
	 *            Buffered bit stream of the file to be read
	 * @param out
	 *            Buffered bit stream writing to the output file. 
	 *             
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (bits == 0) current = current.myLeft;
				else current = current.myRight;
				/* Check if the current node is a leaf node
				 * Stop reading when PSEUDO_EOF character is reached
				 * Write a leaf value to the output stream
				 */
				if(current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) {
						break;
					}
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
}