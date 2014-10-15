import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;


public class LyricGeneration {
	final static String  corpusName = "resources/corpus";
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		
		MaxentTagger tagger = new MaxentTagger(
				"taggers/english-left3words-distsim.tagger");
		ArrayList<String> lines = readCorpus(corpusName);
		ArrayList<String> posTaggedLines = tagLines(lines, tagger);
		ArrayList<String> posTemplates = createPosTemplates(posTaggedLines);
		// Output the POS Templates
		System.err.println(Arrays.toString(posTemplates.toArray()));
		
		
		Word[] uniqueWordList = getUniqueWords(posTaggedLines);
		Arrays.sort(uniqueWordList); // Sort by POS tag
		
		int[][] biGram = new int[uniqueWordList.length][uniqueWordList.length];
		for (String ptl : posTaggedLines) {
			String[] words = ptl.split(" ");
			for (String word : words) {
				Word a = new Word(word);
			
			}
		}
		
		
		
		
		
		
		return;
	}
	
	private static int binary_search(Word[] wordList, Word word, int imin, int imax){
	  if (imax < imin){
		  return -1;
	  }else{
	      // calculate midpoint to cut set in half
	      int imid = (imin + imax) /2;
	      int comparison = wordList[imid].compareTo(word);
	    		  
	      // three-way comparison
	      if (comparison < 0)
	        // key is in lower subset
	        return binary_search(wordList, word, imin, imid-1);
	      else if (comparison > 0)
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
			if(POSTemplateOccurances.get(key) > 2 ) posTemplates.add(key);
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
					lines.add(srcline.replaceAll("[^\\w\\s']","").replaceAll("' ", " ").toLowerCase());
				}
				srcline = reader.readLine();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return lines;
	}
	
}

class Word implements Comparable<Word>{
	String original;
	String word; 
	String POS;
	public Word(String word){
		String[] tokens = word.split("_");
		if(tokens.length != 2) throw new IllegalArgumentException();
		original = word;
		this.word = tokens[0];
		this.POS = tokens[1];
	}
	
	public Word(String word, String POS){
		this.word= word; 
		this.POS = POS;
	}
	@Override
	public int compareTo(Word o) {
		return (POS+word).compareTo(o.POS + o.word);
	}
	@Override
	public int hashCode() {
		// TODO Auto-generated method stub
		if(original == null) return (word + POS).hashCode();
		return original.hashCode();
	}
	public String toString(){
		return word + ":" + POS + " " + hashCode();
	}
	@Override
	public boolean equals(Object obj) {
		// TODO Auto-generated method stub
		return hashCode() == obj.hashCode();
	}
}

