import { GoogleGenAI, Type } from "@google/genai";
import type { AnalysisResult, Severity } from '../types';

const fileToBase64 = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => {
      const result = reader.result as string;
      // remove the header from the base64 string
      resolve(result.split(',')[1]);
    };
    reader.onerror = (error) => reject(error);
  });
};

const ai = new GoogleGenAI({ apiKey: process.env.API_KEY as string });

export const analyzeHseCase = async (
  image: File,
  description: string,
  location: string
): Promise<AnalysisResult> => {
    const imageBase64 = await fileToBase64(image);
    
    const prompt = `
    As an expert in Health, Safety, and Environment (HSE) in a factory setting, analyze the following case:
    - Location: ${location}
    - Problem Description: ${description}

    Based on the attached image and description, identify the following information and return it exclusively in JSON format.
    For the fields 'hazardType', 'proposedSolution', and 'responsiblePerson', you MUST provide the content in BOTH Arabic and French.

    1.  **hazardType**: An object with two keys, "ar" for the Arabic translation and "fr" for the French translation of the hazard type (e.g., electrical, mechanical, chemical).
    2.  **severity**: Assess the severity level. Use one of the following values ONLY: "low", "medium", "high", "critical".
    3.  **proposedSolution**: An object with "ar" and "fr" keys containing the recommended corrective action in both languages.
    4.  **responsiblePerson**: An object with "ar" and "fr" keys suggesting the appropriate person or department (e.g., Maintenance Department, Electrical Team) in both languages.
    `;

    const response = await ai.models.generateContent({
        model: "gemini-2.5-flash",
        contents: {
            parts: [
                {
                    inlineData: {
                        mimeType: image.type,
                        data: imageBase64
                    }
                },
                { text: prompt }
            ]
        },
        config: {
            responseMimeType: "application/json",
            responseSchema: {
                type: Type.OBJECT,
                properties: {
                    hazardType: {
                        type: Type.OBJECT,
                        properties: {
                            ar: { type: Type.STRING },
                            fr: { type: Type.STRING }
                        },
                        required: ["ar", "fr"]
                    },
                    severity: { type: Type.STRING },
                    proposedSolution: {
                        type: Type.OBJECT,
                        properties: {
                            ar: { type: Type.STRING },
                            fr: { type: Type.STRING }
                        },
                        required: ["ar", "fr"]
                    },
                    responsiblePerson: {
                        type: Type.OBJECT,
                        properties: {
                            ar: { type: Type.STRING },
                            fr: { type: Type.STRING }
                        },
                        required: ["ar", "fr"]
                    }
                },
                required: ["hazardType", "severity", "proposedSolution", "responsiblePerson"]
            }
        }
    });

    try {
        const resultText = response.text.trim();
        const parsedResult = JSON.parse(resultText);

        const validSeverities: Severity[] = ['low', 'medium', 'high', 'critical'];
        if (!validSeverities.includes(parsedResult.severity)) {
            console.error(`Invalid severity received from AI: ${parsedResult.severity}. Defaulting to 'medium'.`);
            parsedResult.severity = 'medium';
        }

        return parsedResult as AnalysisResult;
    } catch (e) {
        console.error("Failed to parse Gemini response:", response.text);
        throw new Error("Could not parse the analysis result from the AI.");
    }
};