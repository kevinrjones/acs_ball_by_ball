export interface Envelope<T> {
  result: T;
  errorMessage: string;
  timeGenerated: string | Date;
}
