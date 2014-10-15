import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
					lines.add(srcline.replaceAll("[^\\w\\s']","").replaceAll("' ", " "));
				}
				srcline = reader.readLine();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return lines;
	}
	public LyricGeneration(){
		
	}
}

class Word implements Comparable<Word>{
	String word; 
	String POS;
	public Word(String word, String POS){
		this.word= word; 
		this.POS = POS;
	}
	@Override
	public int compareTo(Word o) {
		return this.POS.compareTo(o.POS);
	}
}

