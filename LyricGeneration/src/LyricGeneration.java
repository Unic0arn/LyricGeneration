import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Stack;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;


public class LyricGeneration {

	private static final String corpusType = "U2";
	private static String  corpusName = "resources/"+corpusType+"corpus";
	private static String  relDBfile = "resources/"+corpusType+"reldb";
	private static final boolean debug = false;
	private static final int nrDublicatedPosTemplates = 2;
	private static final int NN = 12;
	private static final int PDT = 15;
	private static final int VB = 26;
	private static final int WDT = 32;
	private static final String VNname = "resources/vn";

	//final static String[] POSTags = {"CC","CD","DT","EX","FW","IN","JJ","JJR","JJS","LS","MD","NN","NNS","NNP","NNPS","PDT","POS","PRP","PRP$","RB","RBR","RBS","RP","SYM","TO","UH","VB","VBD","VBG","VBN","VBP","VBZ","WDT","WP","WP$","WRB","RS","RB"};
	/**
	 * @param args
	 */
	public static void main(String[] args) {

		MaxentTagger tagger = new MaxentTagger(
				"taggers/english-left3words-distsim.tagger");
		ArrayList<String> lines = readCorpus(corpusName);
		ArrayList<String> posTaggedLines = tagLines(lines, tagger);
		ArrayList<String> posTemplates = createPosTemplates(posTaggedLines);
		// Output the POS Templates

		if(debug)System.err.println(Arrays.toString(posTemplates.toArray()));

		Word[] uniqueWordList = getUniqueWords(posTaggedLines);
		String[] POSTags = getUniquePOSTags(posTaggedLines);
		Arrays.sort(POSTags);
		Arrays.sort(uniqueWordList); // Sort by POS tag
		/*
			for (int i = 0; i < uniqueWordList.length; i++) {

			System.out.println(i + " "+ uniqueWordList[i].word +" : " + uniqueWordList[i].POS );			
		}*/

		int[] indexes = prepareIndexes(uniqueWordList, POSTags);

		int verbsStart = indexes[Arrays.binarySearch(POSTags, "VB")];
		int verbsEnd = indexes[Arrays.binarySearch(POSTags, "WDT")];
		int nounStart = indexes[Arrays.binarySearch(POSTags, "NN")];
		int nounEnd =  indexes[Arrays.binarySearch(POSTags, "PDT")];		
		boolean[][] vbNNrelation = readRelations(tagger, uniqueWordList,verbsStart, verbsEnd, nounStart, nounEnd);		

		//get start of line and end of line
		int SOL = indexes[Arrays.binarySearch(POSTags, "SOL")];
		int EOL = indexes[Arrays.binarySearch(POSTags, "EOL")];



		//print statistics
		System.out.println("number of postagged lines in corpus: " + posTaggedLines.size());
		System.out.println("number of unique words in corpus: " + uniqueWordList.length);
		System.out.println("number postagTemplates: " + posTemplates.size());		


		// for bigrams:
		double[][] bigrams = calcBigrams(posTaggedLines, uniqueWordList, POSTags, indexes, SOL, EOL);
		for (int i = 0; i < 5; i++) {
			synthesizeRow(posTemplates, uniqueWordList, POSTags,indexes, SOL, bigrams, vbNNrelation);
		}


		//for trigrams:
		//byte[][][] trigrams = calcTrigrams(posTaggedLines, uniqueWordList, POSTags, indexes, SOL, EOL);
		//String verserow = synthesizeRowTriGram(posTemplates, uniqueWordList, POSTags,indexes, SOL, trigrams);


		return;
	}

	private static ArrayList<Integer> getNextWordSemantic(String[] posTemplate,	int indexOfPT, double[][] bigrams, int prevWord, int prevVB, String[] pOSTags, int[] indexes, boolean[][] vbNNrelation) {
		if(indexOfPT == posTemplate.length){
			return new ArrayList<Integer>();
		}
		int aPOSTagNr = Arrays.binarySearch(pOSTags, posTemplate[indexOfPT]);
		int aMin = indexes[aPOSTagNr];
		int aMax = indexes[aPOSTagNr != pOSTags.length-1 ? aPOSTagNr +1 : pOSTags.length-1];
		WordProb[] flwWordProbs = new WordProb[aMax-aMin];
		for (int i = aMin; i < aMax; i++) {
			flwWordProbs[i-aMin] = new WordProb(bigrams[prevWord][i], i);
		}
		int lastVB;
		Arrays.sort(flwWordProbs);

		if(indexOfPT == 0){
			flwWordProbs = randomOrder(flwWordProbs);
		}
		for (int i = 0; i < flwWordProbs.length; i++) {
			//if verb was previously cleared and backtracked, set it as prevVB again
			lastVB = prevVB;
			if(flwWordProbs[i].prob == 0.0){
				//if no chance of word - break (since array is sorted)
				break;
			}
			if(debug)System.err.println("index of POStemplate = " + indexOfPT + " trying with word: "+flwWordProbs[i].index + " with prob: " + flwWordProbs[i].prob);

			//if current postag is a verb, mark it.
			if(aPOSTagNr >= VB && aPOSTagNr < WDT){
				prevVB = flwWordProbs[i].index;
			}

			//if current posTag is NN
			if(aPOSTagNr >= NN && aPOSTagNr < PDT){
				//and if a previous verb is marked 
				if(lastVB > 0){
					//and they are not compatible - continue with loop.
					if(!vbNNrelation[prevVB][flwWordProbs[i].index]){
						continue;		
					}
					//it passed the check, set verb as cleared
					lastVB =-1;
				}
			}

			ArrayList<Integer> nextWordindex = getNextWordSemantic(posTemplate, indexOfPT+1, bigrams, flwWordProbs[i].index, lastVB, pOSTags, indexes, vbNNrelation);
			if(nextWordindex == null){
				continue;
			}else{
				nextWordindex.add(flwWordProbs[i].index);
				if(debug)System.err.println("index of POStemplate = " + indexOfPT + " words added: " + Arrays.toString(nextWordindex.toArray()));
				return nextWordindex;
			}
		}
		return null;
	}

	private static boolean[][] readRelations(MaxentTagger tagger, Word[] uniqueWordList, int vbStart, int vbEnd, int nnStart, int nnEnd) {
		boolean[][] vbNNrelation = new boolean[uniqueWordList.length][uniqueWordList.length];
		BufferedReader reader;
		ArrayList<String> taggedLines = new ArrayList<String>();
		String srcline;		
		try {			
			ObjectInputStream deserializer = new ObjectInputStream(new FileInputStream(relDBfile));
			vbNNrelation = (boolean[][]) deserializer.readObject();
			deserializer.close();
		}catch (FileNotFoundException e){
			try {			
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(VNname), "ISO-8859-1"));
				srcline = reader.readLine();
				while(srcline != null){
					if(srcline.length() != 0){
						taggedLines.add(tagger.tagString(srcline));					
					}
					srcline = reader.readLine();
				}
				reader.close();
				int nrOFrels = 0;
				for (String taggedLine : taggedLines) {
					String[] vbNN = taggedLine.split(" ");
					int vbindex = binary_search(uniqueWordList, new Word(vbNN[0]), vbStart, vbEnd);
					//if word is in corpus / uniqueWordList
					if(vbindex > 0){
						int nnindex = binary_search(uniqueWordList, new Word(vbNN[1]), nnStart, nnEnd);
						//if noun is also in corpus / unique wordlist
						if(nnindex > 0){
							nrOFrels++;						
							vbNNrelation[vbindex][nnindex] = true;						
						}
					}
				}
				
				
				ObjectOutputStream serializer = new ObjectOutputStream(new FileOutputStream(new File(relDBfile)));
				serializer.writeObject(vbNNrelation);	
				serializer.close();
				System.err.println("number of relations in corpus: " + nrOFrels);
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		}catch (Exception e3){			
			e3.printStackTrace();
		}


		return vbNNrelation;
	}

	private static byte[][][] calcTrigrams(ArrayList<String> posTaggedLines,
			Word[] uniqueWordList, String[] POSTags, int[] indexes, int SOL,
			int EOL) {
		byte[][][] triGram = new byte[uniqueWordList.length][uniqueWordList.length][uniqueWordList.length];
		for (String ptl : posTaggedLines) {
			String[] words = ptl.split(" ");
			for (int j = 0; j < words.length-2; j++) {
				Word a = new Word(words[j]);
				Word b = new Word(words[j+1]);
				Word c = new Word(words[j+2]);
				int aIndex = findWordIndex(uniqueWordList, POSTags, indexes, a);
				int bIndex = findWordIndex(uniqueWordList, POSTags, indexes, b);
				int cIndex = findWordIndex(uniqueWordList, POSTags, indexes, c);

				triGram[aIndex][bIndex][cIndex]++;

				if(j==0){
					triGram[SOL][SOL][aIndex]++;
				}else if(j==1){
					triGram[SOL][aIndex][bIndex]++;
				}else if(j==words.length-2){
					triGram[bIndex][cIndex][EOL]++;
				}else if(j==words.length-2){
					triGram[cIndex][EOL][EOL]++;
				}

			}
		}
		return triGram;
	}

	/**
	 * @param posTemplates
	 * @param uniqueWordList
	 * @param POSTags
	 * @param indexes
	 * @param SOL
	 * @param bigrams
	 * @return
	 */
	private static String synthesizeRow(ArrayList<String> posTemplates,
			Word[] uniqueWordList, String[] POSTags, int[] indexes, int SOL,
			double[][] bigrams,boolean[][] vbNNrelation) {
		Random r = new Random();

		String[] posTemplate = posTemplates.get(r.nextInt(posTemplates.size())).split(" ");			

		if(debug)System.out.println(Arrays.toString(posTemplate));
		System.out.println(Arrays.toString(posTemplate));
		StringBuilder sb = new StringBuilder();
		int indexOfPT = 0;
		int prevWord = SOL;
		int prevVB = -1;
		ArrayList<Integer> row = new ArrayList<Integer>();
		ArrayList<Integer> rowSem = new ArrayList<Integer>();

		row = getNextWord(posTemplate , indexOfPT, bigrams, prevWord, POSTags, indexes);

		rowSem = getNextWordSemantic(posTemplate , indexOfPT, bigrams, prevWord, prevVB, POSTags, indexes, vbNNrelation);

		if(rowSem == null){			
			return "unable to produce row for POS-template " + Arrays.toString(posTemplate);
		}
		System.out.println("Without semantics:");
		for (int i = row.size(); i > 0; i--) {			
			sb.append(uniqueWordList[row.get(i-1)].toString2()+" ");			
		}
		System.out.println(sb);

		System.out.println("With semantics:");
		sb = new StringBuilder();
		for (int i = rowSem.size(); i > 0; i--) {			
			sb.append(uniqueWordList[rowSem.get(i-1)].toString2()+" ");			
		}
		System.out.println(sb);

		return sb.toString();

	}

	private static ArrayList<Integer> getNextWord(String[] posTemplate, int indexOfPT, double[][] bigrams, int prevWord, String[] POSTags, int[] indexes) {
		if(indexOfPT == posTemplate.length){
			return new ArrayList<Integer>();
		}
		int aPOSTagNr = Arrays.binarySearch(POSTags, posTemplate[indexOfPT]);
		int aMin = indexes[aPOSTagNr];
		int aMax = indexes[aPOSTagNr != POSTags.length-1 ? aPOSTagNr +1 : POSTags.length-1];
		WordProb[] flwWordProbs = new WordProb[aMax-aMin];
		for (int i = aMin; i < aMax; i++) {
			flwWordProbs[i-aMin] = new WordProb(bigrams[prevWord][i], i);
		}
		Arrays.sort(flwWordProbs);
		if(indexOfPT == 0){
			flwWordProbs = randomOrder(flwWordProbs);
		}

		for (int i = 0; i < flwWordProbs.length; i++) {

			if(flwWordProbs[i].prob == 0.0){
				//if no chance of word - break (since array is sorted)
				break;
			}
			if(debug)System.err.println("index of POStemplate = " + indexOfPT + " trying with word: "+flwWordProbs[i].index + " with prob: " + flwWordProbs[i].prob);
			ArrayList<Integer> nextWordindex = getNextWord(posTemplate, indexOfPT+1, bigrams, flwWordProbs[i].index, POSTags, indexes);
			if(nextWordindex == null){
				continue;
			}else{
				nextWordindex.add(flwWordProbs[i].index);
				if(debug)System.err.println("index of POStemplate = " + indexOfPT + " words added: " + Arrays.toString(nextWordindex.toArray()));
				return nextWordindex;
			}
		}
		return null;
	}



	private static WordProb[] randomOrder(WordProb[] flwWordProbs) {		
		ArrayList<WordProb> temp = new ArrayList<WordProb>();
		for(int i = 0; i<flwWordProbs.length; i++){
			if(flwWordProbs[i].prob == 0){
				break;
			}
			temp.add(flwWordProbs[i]);			
		}		
		Collections.shuffle(temp);

		WordProb[] flwWordProbsres = new WordProb[temp.size()];
		for (int i = 0; i < flwWordProbsres.length; i++) {			
			flwWordProbsres[i] = temp.get(i);
		}

		return flwWordProbsres;
	}


	private static String synthesizeRowTriGram(ArrayList<String> posTemplates,
			Word[] uniqueWordList, String[] POSTags, int[] indexes, int SOL,
			byte[][][] trigrams) {
		Random r = new Random();
		String[] posTemplate = posTemplates.get(r.nextInt(posTemplates.size())).split(" ");		
		if(debug)System.out.println(Arrays.toString(posTemplate));
		StringBuilder sb = new StringBuilder();
		int indexOfPT = 0;
		int prevWord = SOL; 
		ArrayList<Integer> row = new ArrayList<Integer>();
		row = getNextWordTriGram(posTemplate , indexOfPT, trigrams, prevWord,prevWord, POSTags, indexes);
		for (int i = row.size(); i > 0; i--) {			
			sb.append(uniqueWordList[row.get(i-1)].toString2()+" ");			
		}			
		return sb.toString();
	}

	private static ArrayList<Integer> getNextWordTriGram(String[] posTemplate, int indexOfPT, 
			byte[][][] trigrams, int prevPrevWord, int prevWord,  String[] POSTags, int[] indexes) {
		if(indexOfPT == posTemplate.length){
			return new ArrayList<Integer>();
		}
		int aPOSTagNr = Arrays.binarySearch(POSTags, posTemplate[indexOfPT]);
		int aMin = indexes[aPOSTagNr];
		int aMax = indexes[aPOSTagNr != POSTags.length-1 ? aPOSTagNr +1 : POSTags.length-1];
		WordProb[] flwWordProbs = new WordProb[aMax-aMin];
		for (int i = aMin; i < aMax; i++) {
			flwWordProbs[i-aMin] = new WordProb(trigrams[prevPrevWord][prevWord][i], i);
		}
		Arrays.sort(flwWordProbs);
		for (int i = 0; i < flwWordProbs.length; i++) {
			ArrayList<Integer> nextWordindex = getNextWordTriGram(posTemplate, indexOfPT+1, trigrams,prevWord , flwWordProbs[i].index, POSTags, indexes);
			if(nextWordindex == null){
				continue;
			}else{
				nextWordindex.add(flwWordProbs[i].index);
				return nextWordindex;
			}
		}
		return null;
	}

	/**
	 * @param posTaggedLines
	 * @param uniqueWordList
	 * @param POSTags
	 * @param indexes
	 * @param SOL
	 * @param EOL
	 */
	private static double[][] calcBigrams(ArrayList<String> posTaggedLines,	Word[] uniqueWordList, String[] POSTags, int[] indexes, int SOL, int EOL) {
		double[][] biGram = new double[uniqueWordList.length][uniqueWordList.length];
		for (String ptl : posTaggedLines) {
			String[] words = ptl.split(" ");
			Word a;
			Word b;
			for (int j = 0; j < words.length; j++) {


				a = new Word(words[j]);

				int aIndex = findWordIndex(uniqueWordList, POSTags, indexes, a);
				//if first word in row
				if(j==0){
					biGram[SOL][aIndex]++;
				}
				if(j < words.length-1){
					//if have next word in row
					b = new Word(words[j+1]);
					int bIndex = findWordIndex(uniqueWordList, POSTags, indexes, b);
					biGram[aIndex][bIndex]++;
				}else{
					//last word. add EOL as next word.
					biGram[aIndex][EOL]++;
				}				
			}
		}
		//Normalize rows
		double[] sums = new double[biGram.length];
		for (int i = 0; i < biGram.length; i++) {
			double rowSum = 0.0;
			for (int j = 0; j < biGram.length; j++) {
				rowSum += biGram[i][j];
			}
			sums[i] = rowSum;
			rowSum = 1/rowSum;

			for (int j = 0; j < biGram.length; j++) {
				biGram[i][j] = biGram[i][j] * rowSum;
				if(debug && Double.isNaN(biGram[i][j])){
					System.err.println("Nan found");
				}
			}
		}
		return biGram;
	}

	/**
	 * @param uniqueWordList
	 * @param POSTags
	 * @param indexes
	 * @param a
	 * @return
	 */
	private static int findWordIndex(Word[] uniqueWordList, String[] POSTags,
			int[] indexes, Word a) {
		int aPOSTagNr = Arrays.binarySearch(POSTags, a.POS);
		int aMin = indexes[aPOSTagNr];
		int aMax = aPOSTagNr == POSTags.length-1 ? uniqueWordList.length : indexes[aPOSTagNr + 1 ];
		int aIndex = binary_search(uniqueWordList, a, aMin, aMax);
		return aIndex;
	}

	private static String[] getUniquePOSTags(ArrayList<String> posTaggedLines) {
		HashSet<String> posList = new HashSet<String>();
		posList.add("SOL");
		posList.add("EOL");
		for (String taggedLine : posTaggedLines) {
			String[] words = taggedLine.split(" ");
			for (String word : words) {
				String[] tokens = word.split("_");
				if(!posList.contains(tokens[1]))posList.add(tokens[1]);
			}
		}
		String[] POSTags = new String[posList.size()];
		posList.toArray(POSTags);
		return POSTags;
	}
	/**
	 * 
	 * @param wordList
	 * @param POSTags
	 * @return
	 */
	private static int[] prepareIndexes(Word[] wordList, String[] POSTags){
		int[] indexes = new int[POSTags.length];
		int POSTag = 0;
		indexes[POSTag] = 0;
		String currentPOSTag = POSTags[POSTag];
		POSTag++;
		for (int i = 0; i < wordList.length; i++) {
			Word curWord = wordList[i];
			if(!currentPOSTag.equals(curWord.POS)){
				if(debug)System.err.println("Going to next POSTAG " + currentPOSTag + " -> " + curWord.POS + " at index: " + i);
				currentPOSTag = curWord.POS;
				indexes[POSTag] = i;
				POSTag++;
			}
		}
		return indexes;
	}


	private static int binary_search(Word[] wordList, Word word, int imin, int imax){
		if (imax < imin){
			return -1;
		}else{
			// calculate midpoint to cut set in half
			int imid = (imin + imax) /2;
			int comparison = wordList[imid].compareTo(word);

			// three-way comparison
			if (comparison > 0)
				// key is in lower subset
				return binary_search(wordList, word, imin, imid-1);
			else if (comparison < 0)
				// key is in upper subset
				return binary_search(wordList, word, imid+1, imax);
			else
				// key has been found
				return imid;
		}
	}
	private static Word[] getUniqueWords(ArrayList<String> posTaggedLines) {
		HashSet<Word> wordList = new HashSet<Word>();
		for (String taggedLine : posTaggedLines) {
			String[] words = taggedLine.split(" ");
			for (String word : words) {
				wordList.add(new Word(word));
			}
		}
		wordList.add(new Word("SOL","SOL"));
		wordList.add(new Word("EOL","EOL"));
		Word[] words = new Word[wordList.size()];
		wordList.toArray(words);
		return words;
	}
	private static ArrayList<String> createPosTemplates(ArrayList<String> posTaggedLines) {
		ArrayList<String> posTemplates = new ArrayList<String>();

		HashMap<String, Integer> POSTemplateOccurances = new HashMap<String, Integer>();
		for (String taggedLine : posTaggedLines) {
			taggedLine = taggedLine.replaceAll("['\\w]+_|\\s$", ""); //Remove the actual words leaving only the POS-tags
			Integer prev = POSTemplateOccurances.get(taggedLine);
			if(prev != null){
				POSTemplateOccurances.put(taggedLine, prev +1);
			}else{
				POSTemplateOccurances.put(taggedLine, 1);
			}
		}
		Set<String> keys = POSTemplateOccurances.keySet();
		for (String key : keys) {
			if(POSTemplateOccurances.get(key) > nrDublicatedPosTemplates ) posTemplates.add(key);
		}
		return posTemplates;
	}
	private static ArrayList<String> tagLines(ArrayList<String> lines, MaxentTagger tagger){
		ArrayList<String> posTaggedLines = new ArrayList<String>();
		for (String line : lines) {
			String taggedLine = tagger.tagString(line);
			posTaggedLines.add(taggedLine);
		}
		return posTaggedLines;
	}
	private static ArrayList<String> readCorpus(String name) {
		BufferedReader reader;
		String srcline;
		ArrayList<String> lines = new ArrayList<String>();
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(name), "ISO-8859-1"));
			srcline = reader.readLine();
			while(srcline != null){
				if(srcline.length() != 0){
					lines.add(srcline.replaceAll("[^\\w\\s']","").replaceAll("'", " ").replaceAll("'\\n","").toLowerCase());
				}
				srcline = reader.readLine();
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return lines;
	}

}

class WordProb implements Comparable<WordProb>{

	double prob;
	int index;
	public WordProb(double prob, int index) {
		super();
		this.prob = prob;
		this.index = index;
	}
	@Override
	public int compareTo(WordProb o) {		
		return Double.compare(o.prob, prob);
	}

	@Override
	public String toString(){
		return index + " : " + prob ;
	}

}

class Word implements Comparable<Word>{
	String original;
	String word; 
	String POS;
	public Word(String word){
		String[] tokens = word.split("_");
		if(tokens.length != 2){
			System.err.println(Arrays.toString(tokens));
			throw new IllegalArgumentException();
		}
		if(tokens[0].equals("'")){
			System.err.println("found ' in corpus");
		}
		original = word;
		this.word = tokens[0];
		this.POS = tokens[1];
	}

	public Object toString2() {		
		return word;
	}

	public Word(String word, String POS){
		this.word= word; 
		this.POS = POS;
	}
	@Override
	public int compareTo(Word o) {
		return (POS+" "+word).compareTo(o.POS +" "+ o.word);
	}
	@Override
	public int hashCode() {		
		if(original == null) return (word + POS).hashCode();
		return original.hashCode();
	}
	public String toString(){
		return word + ":" + POS + " " + hashCode();
	}
	@Override
	public boolean equals(Object obj) {		
		return hashCode() == obj.hashCode();
	}
}

