export type Severity = 'low' | 'medium' | 'high' | 'critical';

export interface LocalizedString {
  ar: string;
  fr: string;
}

export interface HseReport {
  id: string;
  date: string;
  location: string;
  hazardType: LocalizedString;
  severity: Severity;
  proposedSolution: LocalizedString;
  image: string;
  responsiblePerson: LocalizedString;
}

export interface AnalysisResult {
  hazardType: LocalizedString;
  severity: Severity;
  proposedSolution: LocalizedString;
  responsiblePerson: LocalizedString;
}